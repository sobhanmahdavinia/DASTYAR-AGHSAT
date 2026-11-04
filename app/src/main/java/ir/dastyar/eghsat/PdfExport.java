package ir.dastyar.eghsat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Monthly PDF report: for a chosen Jalali month (or every month) it lists the installments that are
 * paid, not paid yet, and overdue. Purely additive - it only READS the stored installments and uses the
 * same paid / overdue rules as the home screen, so nothing about the saved data changes.
 *
 * The PDF is drawn with the platform's PdfDocument (no extra library) and saved to
 * Download/DastyarEghsat through MediaStore (no storage permission needed).
 */
final class PdfExport {
    private PdfExport() {}

    // ------------------------------------------------------------------ model

    static final int PAID = 0, UNPAID = 1, OVERDUE = 2;

    private static final class Row {
        final Installment x; final int index; final long due;
        Row(Installment x, int index, long due) { this.x = x; this.index = index; this.due = due; }
    }

    private static final class MonthReport {
        final int year, month;
        final List<Row> paid = new ArrayList<>(), unpaid = new ArrayList<>(), overdue = new ArrayList<>();
        long paidSum, unpaidSum, overdueSum;
        MonthReport(int year, int month) { this.year = year; this.month = month; }
        boolean isEmpty() { return paid.isEmpty() && unpaid.isEmpty() && overdue.isEmpty(); }
        int count() { return paid.size() + unpaid.size() + overdue.size(); }
        long total() { return paidSum + unpaidSum + overdueSum; }
        String title() { return PersianDate.MONTH_NAMES[month - 1] + " " + year; }
    }

    /** Same rules as MainActivity.refresh(): paid = index < paidCount; overdue = unpaid and past its due time. */
    private static MonthReport buildMonth(List<Installment> all, int jy, int jm, long now) {
        MonthReport r = new MonthReport(jy, jm);
        for (Installment x : all) {
            int idx = x.paymentIndexForMonth(jy, jm);
            if (idx < 0) continue;
            long due = x.dueMillisForIndex(idx);
            Row row = new Row(x, idx, due);
            if (idx < x.paidCount) { r.paid.add(row); r.paidSum += x.amount; }
            else if (due < now) { r.overdue.add(row); r.overdueSum += x.amount; }
            else { r.unpaid.add(row); r.unpaidSum += x.amount; }
        }
        java.util.Comparator<Row> byDue = (a, b) -> Long.compare(a.due, b.due);
        Collections.sort(r.paid, byDue); Collections.sort(r.unpaid, byDue); Collections.sort(r.overdue, byDue);
        return r;
    }

    private static int monthKey(int jy, int jm) { return jy * 12 + (jm - 1); }

    private static int[] todayJalali() {
        Calendar n = Calendar.getInstance();
        return PersianDate.toJalali(n.get(Calendar.YEAR), n.get(Calendar.MONTH) + 1, n.get(Calendar.DAY_OF_MONTH));
    }

    private static String jalaliText(long millis) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(millis);
        int[] j = PersianDate.toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        return String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]);
    }

    /** {firstMonthKey, lastMonthKey}: every month that has at least one installment, always including this month. */
    private static int[] monthRange(List<Installment> all, int[] today) {
        int min = monthKey(today[0], today[1]), max = min;
        for (Installment x : all) {
            Calendar c = Calendar.getInstance(); c.setTimeInMillis(x.firstDueMillis);
            int[] j = PersianDate.toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            int first = monthKey(j[0], j[1]);
            int last = first + Math.max(1, x.totalCount) - 1;
            if (first < min) min = first;
            if (last > max) max = last;
        }
        return new int[]{min, max};
    }

    // ------------------------------------------------------------------ PDF drawing

    private static final int PAGE_W = 595, PAGE_H = 842, MARGIN = 32, TOP = 32, FOOTER_H = 36;
    private static final int CONTENT_W = PAGE_W - 2 * MARGIN;
    /** columns from right to left: #, title, bank, installment no., due date, amount (sum = CONTENT_W) */
    private static final int[] COL_W = {30, 160, 85, 66, 74, 116};
    private static final Layout.Alignment[] COL_ALIGN = {
            Layout.Alignment.ALIGN_CENTER, Layout.Alignment.ALIGN_NORMAL, Layout.Alignment.ALIGN_NORMAL,
            Layout.Alignment.ALIGN_CENTER, Layout.Alignment.ALIGN_CENTER, Layout.Alignment.ALIGN_CENTER};
    private static final String[] COL_TITLE = {"ردیف", "عنوان قسط", "بانک", "قسط", "سررسید", "مبلغ"};

    private static final int INK = 0xFF182332, MUTED = 0xFF67778B, LINE = 0xFFE3E8EF, ZEBRA = 0xFFF6F8FB, HEAD_BG = 0xFFEAF0F7;
    private static final int C_BRAND = 0xFF2D5B99, C_PAID = 0xFF168A58, C_UNPAID = 0xFF2D5B99, C_OVERDUE = 0xFFC62828;

    private static TextPaint paint(float size, int color, boolean bold) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size); p.setColor(color);
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return p;
    }

    /** Right-to-left text block. ALIGN_NORMAL = right edge, ALIGN_OPPOSITE = left edge. */
    private static StaticLayout layout(String s, TextPaint p, int width, int maxLines, Layout.Alignment a) {
        StaticLayout.Builder b = StaticLayout.Builder.obtain(s, 0, s.length(), p, Math.max(1, width))
                .setAlignment(a).setTextDirection(TextDirectionHeuristics.RTL).setIncludePad(false);
        if (maxLines > 0) { b.setMaxLines(maxLines); b.setEllipsize(TextUtils.TruncateAt.END); }
        return b.build();
    }

    private static final class Writer {
        final PdfDocument doc = new PdfDocument();
        PdfDocument.Page page; Canvas cv; int pageNo = 0; float y; String running = "";
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint body = paint(9.5f, INK, false), bodyBold = paint(9.5f, INK, true), small = paint(8f, MUTED, false),
                overdueDate = paint(9.5f, C_OVERDUE, true), headCell = paint(9f, INK, true),
                white16 = paint(16f, 0xFFFFFFFF, true), white85 = paint(8.5f, 0xE6FFFFFF, false), white105 = paint(10.5f, 0xFFFFFFFF, true);

        Writer() { stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(0.6f); }

        float limit() { return PAGE_H - FOOTER_H - 8; }

        void newPage(boolean continuation) {
            finishPage();
            pageNo++;
            page = doc.startPage(new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create());
            cv = page.getCanvas(); y = TOP;
            if (continuation && !running.isEmpty()) {
                y += text(running + " - ادامه", small, MARGIN, y, CONTENT_W, 1, Layout.Alignment.ALIGN_NORMAL) + 8;
            }
        }

        void finishPage() {
            if (page == null) return;
            stroke.setColor(LINE);
            cv.drawLine(MARGIN, PAGE_H - FOOTER_H, PAGE_W - MARGIN, PAGE_H - FOOTER_H, stroke);
            text("دستیار اقساط", small, MARGIN, PAGE_H - FOOTER_H + 8, CONTENT_W, 1, Layout.Alignment.ALIGN_NORMAL);
            text("صفحه " + pageNo, small, MARGIN, PAGE_H - FOOTER_H + 8, CONTENT_W, 1, Layout.Alignment.ALIGN_OPPOSITE);
            doc.finishPage(page); page = null; cv = null;
        }

        /** draws a text block with its top-left at (left, top); returns its height */
        float text(String s, TextPaint p, float left, float top, int width, int maxLines, Layout.Alignment a) {
            StaticLayout l = layout(s, p, width, maxLines, a);
            cv.save(); cv.translate(left, top); l.draw(cv); cv.restore();
            return l.getHeight();
        }

        void round(float l, float t, float r, float b, int color, float radius) {
            fill.setColor(color); cv.drawRoundRect(new RectF(l, t, r, b), radius, radius, fill);
        }

        void rect(float l, float t, float r, float b, int color) { fill.setColor(color); cv.drawRect(l, t, r, b, fill); }

        // ---- one month ----
        void month(MonthReport r, String todayText) {
            running = "اقساط " + r.title();
            newPage(false);

            // header band
            float barH = 58;
            round(MARGIN, y, PAGE_W - MARGIN, y + barH, C_BRAND, 12);
            text("گزارش ماهانه اقساط", white16, PAGE_W - MARGIN - 14 - 260, y + 10, 260, 1, Layout.Alignment.ALIGN_NORMAL);
            text("دستیار اقساط", white85, PAGE_W - MARGIN - 14 - 260, y + 34, 260, 1, Layout.Alignment.ALIGN_NORMAL);
            text(r.title(), white16, MARGIN + 14, y + 10, 200, 1, Layout.Alignment.ALIGN_OPPOSITE);
            text("تاریخ تهیه: " + todayText, white85, MARGIN + 14, y + 34, 200, 1, Layout.Alignment.ALIGN_OPPOSITE);
            y += barH + 12;

            // summary cells (right to left): total, paid, unpaid, overdue
            String[] labels = {"کل اقساط ماه", "پرداخت‌شده", "پرداخت‌نشده", "معوق"};
            long[] sums = {r.total(), r.paidSum, r.unpaidSum, r.overdueSum};
            int[] counts = {r.count(), r.paid.size(), r.unpaid.size(), r.overdue.size()};
            int[] colors = {INK, C_PAID, C_UNPAID, C_OVERDUE};
            int gap = 8, cellW = (CONTENT_W - 3 * gap) / 4; float cellH = 56;
            for (int i = 0; i < 4; i++) {
                float right = PAGE_W - MARGIN - i * (cellW + gap), left = right - cellW;
                round(left, y, right, y + cellH, ZEBRA, 10);
                rect(left + 10, y, right - 10, y + 2.5f, colors[i]);
                text(labels[i], small, left + 4, y + 8, cellW - 8, 1, Layout.Alignment.ALIGN_CENTER);
                text(Ui.money(sums[i]), paint(10f, colors[i], true), left + 4, y + 21, cellW - 8, 1, Layout.Alignment.ALIGN_CENTER);
                text(counts[i] + " قسط", small, left + 4, y + 39, cellW - 8, 1, Layout.Alignment.ALIGN_CENTER);
            }
            y += cellH + 8;
            y += text("پرداخت‌نشده: سررسید هنوز نرسیده   •   معوق: سررسید گذشته و هنوز پرداخت نشده",
                    small, MARGIN, y, CONTENT_W, 2, Layout.Alignment.ALIGN_NORMAL) + 12;

            if (r.isEmpty()) {
                text("در این ماه قسطی برنامه‌ریزی نشده است.", paint(12f, MUTED, false), MARGIN, y + 20, CONTENT_W, 1, Layout.Alignment.ALIGN_CENTER);
                return;
            }
            section("پرداخت‌شده", r.paid, r.paidSum, C_PAID);
            section("پرداخت‌نشده", r.unpaid, r.unpaidSum, C_UNPAID);
            section("معوق", r.overdue, r.overdueSum, C_OVERDUE);
        }

        // ---- a titled table ----
        static final float SEC_H = 24, HEAD_H = 20, ROW_PAD = 5;

        void sectionHeader(String title, int count, long sum, int color) {
            round(MARGIN, y, PAGE_W - MARGIN, y + SEC_H, color, 8);
            text(title + "  (" + count + " قسط)", white105, PAGE_W - MARGIN - 10 - 260, y + 6, 260, 1, Layout.Alignment.ALIGN_NORMAL);
            text("جمع: " + Ui.money(sum), white105, MARGIN + 10, y + 6, 220, 1, Layout.Alignment.ALIGN_OPPOSITE);
            y += SEC_H + 4;
        }

        void columnHeader() {
            rect(MARGIN, y, PAGE_W - MARGIN, y + HEAD_H, HEAD_BG);
            float right = PAGE_W - MARGIN;
            for (int i = 0; i < COL_W.length; i++) {
                float left = right - COL_W[i];
                text(COL_TITLE[i], headCell, left + 4, y + 5, COL_W[i] - 8, 1,
                        i == 1 || i == 2 ? Layout.Alignment.ALIGN_NORMAL : Layout.Alignment.ALIGN_CENTER);
                right = left;
            }
            y += HEAD_H;
        }

        void section(String title, List<Row> rows, long sum, int color) {
            float need = SEC_H + 4 + (rows.isEmpty() ? 24 : HEAD_H + 30);
            if (y + need > limit()) newPage(true);
            sectionHeader(title, rows.size(), sum, color);
            if (rows.isEmpty()) {
                text("موردی در این بخش نیست.", small, MARGIN + 4, y + 4, CONTENT_W - 8, 1, Layout.Alignment.ALIGN_NORMAL);
                y += 26;
                return;
            }
            columnHeader();
            long now = System.currentTimeMillis();
            int n = 0;
            for (Row r : rows) {
                n++;
                boolean late = r.index >= r.x.paidCount && r.due < now;
                StaticLayout[] cells = rowCells(r, n, late);
                float h = ROW_PAD * 2;
                float tallest = 0;
                for (StaticLayout l : cells) tallest = Math.max(tallest, l.getHeight());
                h += tallest;
                if (y + h > limit()) {
                    newPage(true);
                    sectionHeader(title + " - ادامه", rows.size(), sum, color);
                    columnHeader();
                }
                if (n % 2 == 0) rect(MARGIN, y, PAGE_W - MARGIN, y + h, ZEBRA);
                float right = PAGE_W - MARGIN;
                for (int i = 0; i < cells.length; i++) {
                    float left = right - COL_W[i];
                    cv.save(); cv.translate(left + 4, y + ROW_PAD); cells[i].draw(cv); cv.restore();
                    right = left;
                }
                stroke.setColor(LINE);
                cv.drawLine(MARGIN, y + h, PAGE_W - MARGIN, y + h, stroke);
                y += h;
            }
            y += 14;
        }

        StaticLayout[] rowCells(Row r, int n, boolean late) {
            Installment x = r.x;
            Banks.Bank bk = Banks.byId(x.bank);
            String[] t = {
                    String.valueOf(n),
                    x.title.isEmpty() ? "-" : x.title,
                    bk.id.isEmpty() ? "-" : bk.name,
                    (r.index + 1) + " از " + x.totalCount,
                    jalaliText(r.due),
                    Ui.money(x.amount)};
            StaticLayout[] out = new StaticLayout[t.length];
            for (int i = 0; i < t.length; i++) {
                TextPaint p = (i == 4 && late) ? overdueDate : body;
                out[i] = layout(t[i], p, COL_W[i] - 8, i == 1 || i == 2 ? 2 : 1, COL_ALIGN[i]);
            }
            return out;
        }
    }

    // ------------------------------------------------------------------ build + save

    static final class Result {
        final Uri uri; final String name;
        Result(Uri uri, String name) { this.uri = uri; this.name = name; }
    }

    /** @param selectedKey monthKey of the chosen month, or -1 for every month */
    private static Result build(Context ctx, int selectedKey) throws IOException {
        List<Installment> all = Store.all(ctx);
        long now = System.currentTimeMillis();
        int[] today = todayJalali();
        List<MonthReport> reports = new ArrayList<>();
        String fileName;
        if (selectedKey < 0) {
            int[] range = monthRange(all, today);
            for (int k = range[0]; k <= range[1]; k++) {
                MonthReport r = buildMonth(all, k / 12, k % 12 + 1, now);
                if (!r.isEmpty()) reports.add(r);
            }
            if (reports.isEmpty()) reports.add(buildMonth(all, today[0], today[1], now));
            fileName = "Eghsat_all-months.pdf";
        } else {
            int jy = selectedKey / 12, jm = selectedKey % 12 + 1;
            reports.add(buildMonth(all, jy, jm, now));
            fileName = String.format(Locale.US, "Eghsat_%04d-%02d.pdf", jy, jm);
        }

        String todayText = String.format(Locale.US, "%04d/%02d/%02d", today[0], today[1], today[2]);
        Writer w = new Writer();
        try {
            for (MonthReport r : reports) w.month(r, todayText);
            w.finishPage();
            return save(ctx, w.doc, fileName);
        } finally {
            w.doc.close();
        }
    }

    /** Saves into Download/DastyarEghsat through MediaStore (works without any storage permission). */
    private static Result save(Context ctx, PdfDocument doc, String name) throws IOException {
        ContentResolver cr = ctx.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DastyarEghsat");
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new IOException("could not create the file");
        try (OutputStream os = cr.openOutputStream(uri)) {
            if (os == null) throw new IOException("could not open the file");
            doc.writeTo(os);
        } catch (IOException | RuntimeException e) {
            cr.delete(uri, null, null);
            throw e;
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        cr.update(uri, done, null, null);

        String shown = name; // the system may have added " (1)" if the name already existed
        try (Cursor c = cr.query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) shown = c.getString(0);
        } catch (Exception ignored) {}
        return new Result(uri, shown);
    }

    // ------------------------------------------------------------------ UI

    private static boolean alive(Activity a) { return !a.isFinishing() && !a.isDestroyed(); }

    /** Entry point from the side menu: pick a month (or all months), then build and save the PDF. */
    static void showDialog(Activity act, Theme th) {
        List<Installment> all = Store.all(act);
        if (all.isEmpty()) {
            Toast.makeText(act, "هنوز قسطی ثبت نشده که از آن خروجی بگیریم.", Toast.LENGTH_LONG).show();
            return;
        }
        int[] today = todayJalali();
        int[] range = monthRange(all, today);
        final List<String> labels = new ArrayList<>();
        labels.add("همه ماه‌ها (هر ماه یک صفحه)");
        for (int k = range[0]; k <= range[1]; k++) labels.add(PersianDate.MONTH_NAMES[k % 12] + " " + (k / 12));

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 16), Ui.dp(act, 20), Ui.dp(act, 6));
        TextView title = Ui.tv(act, "خروجی PDF ماهانه", 18, th.text);
        title.setTypeface(null, Typeface.BOLD); title.setGravity(Gravity.RIGHT);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = Ui.tv(act, "اقساط پرداخت‌شده، پرداخت‌نشده و معوقِ ماه انتخابی در یک فایل PDF ذخیره می‌شود.", 12, th.muted);
        hint.setGravity(Gravity.RIGHT); hint.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 12));
        box.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        Spinner spinner = new Spinner(act);
        spinner.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        spinner.setBackground(Ui.roundedStroke(act, th.bg, th.border, 14, 1));
        spinner.setPopupBackgroundDrawable(Ui.roundedStroke(act, th.card, th.border, 14, 1));
        spinner.setPadding(Ui.dp(act, 6), 0, Ui.dp(act, 6), 0);
        spinner.setAdapter(new ArrayAdapter<String>(act, 0, labels) {
            private View item(int position, boolean dropdown) {
                TextView t = Ui.tv(act, dropdown ? getItem(position) : getItem(position) + "  ▾", 14, th.text);
                t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                t.setPadding(Ui.dp(act, 12), Ui.dp(act, dropdown ? 12 : 10), Ui.dp(act, 12), Ui.dp(act, dropdown ? 12 : 10));
                return t;
            }
            @Override public View getView(int position, View convertView, ViewGroup parent) { return item(position, false); }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) { return item(position, true); }
        });
        spinner.setSelection(Math.max(0, monthKey(today[0], today[1]) - range[0] + 1)); // this month
        box.addView(spinner, new LinearLayout.LayoutParams(-1, Ui.dp(act, 48)));

        AlertDialog dialog = new AlertDialog.Builder(act).setView(box)
                .setNegativeButton("انصراف", null).setPositiveButton("ساخت PDF", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(th.primary);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(th.muted);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int pos = spinner.getSelectedItemPosition();
                int key = pos <= 0 ? -1 : range[0] + pos - 1;
                dialog.dismiss();
                generate(act, th, key);
            });
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(Ui.rounded(act, th.card, 24));
    }

    private static void generate(Activity act, Theme th, int selectedKey) {
        Toast.makeText(act, "در حال ساخت PDF…", Toast.LENGTH_SHORT).show();
        final Context app = act.getApplicationContext();
        new Thread(() -> {
            try {
                Result r = build(app, selectedKey);
                act.runOnUiThread(() -> { if (alive(act)) showDone(act, th, r); });
            } catch (Exception e) {
                act.runOnUiThread(() -> {
                    if (alive(act)) Toast.makeText(act, "ساخت PDF ناموفق بود. دوباره تلاش کن.", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static void showDone(Activity act, Theme th, Result r) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 16), Ui.dp(act, 20), Ui.dp(act, 6));
        TextView title = Ui.tv(act, "PDF ساخته شد", 18, th.text);
        title.setTypeface(null, Typeface.BOLD); title.setGravity(Gravity.RIGHT);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView msg = Ui.tv(act, "در پوشهٔ Download/DastyarEghsat ذخیره شد:\n" + r.name, 13, th.muted);
        msg.setGravity(Gravity.RIGHT); msg.setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 8));
        box.addView(msg, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(act).setView(box)
                .setPositiveButton("باز کردن", null)
                .setNeutralButton("اشتراک‌گذاری", null)
                .setNegativeButton("بستن", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(th.primary);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(th.primary);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(th.muted);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { dialog.dismiss(); open(act, r.uri); });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { dialog.dismiss(); share(act, r.uri); });
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(Ui.rounded(act, th.card, 24));
    }

    private static void open(Activity act, Uri uri) {
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { act.startActivity(i); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(act, "برنامه‌ای برای نمایش PDF پیدا نشد. فایل در پوشهٔ Download ذخیره شده است.", Toast.LENGTH_LONG).show();
        }
    }

    private static void share(Activity act, Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { act.startActivity(Intent.createChooser(i, "اشتراک‌گذاری PDF")); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(act, "برنامه‌ای برای اشتراک‌گذاری پیدا نشد.", Toast.LENGTH_LONG).show();
        }
    }
}
