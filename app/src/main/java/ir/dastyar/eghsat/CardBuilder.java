package ir.dastyar.eghsat;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Builds one installment row as a swipeable card: the face shows title,
 * bank badge, progress and due date; swiping it reveals پرداخت/ویرایش/حذف
 * underneath, instead of always-visible buttons.
 */
public class CardBuilder {

    public interface Actions {
        void onPay(Installment x);
        void onEdit(Installment x);
        void onDelete(Installment x);
    }

    // Only one swiped-open card at a time, across whichever list is on screen.
    private static View openCard = null;

    static int dp(Activity a, float v) { return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f); }

    static GradientDrawable rounded(Activity a, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(a, radiusDp));
        return g;
    }

    static String money(long n) { return NumberFormat.getInstance(Locale.US).format(n) + " تومان"; }

    public static View build(Activity ctx, Theme th, Installment x, Actions actions) {
        int actionW = dp(ctx, 64);
        int actionsWidth = actionW * (x.isFinished() ? 1 : 3);

        FrameLayout wrap = new FrameLayout(ctx);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(-1, -2);
        wrapLp.setMargins(0, dp(ctx, 10), 0, 0);
        wrap.setLayoutParams(wrapLp);

        // Actions sit pinned to the start edge, hidden behind the face until swiped.
        LinearLayout actionsRow = new LinearLayout(ctx);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setBackground(rounded(ctx, th.card, 14));
        actionsRow.setClipToOutline(true);
        FrameLayout.LayoutParams actionsLp = new FrameLayout.LayoutParams(actionsWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        actionsLp.gravity = Gravity.START;
        wrap.addView(actionsRow, actionsLp);

        if (!x.isFinished()) {
            actionsRow.addView(actionBtn(ctx, "✓", th.success, actionW, v -> { closeOpen(); actions.onPay(x); }));
            actionsRow.addView(actionBtn(ctx, "✎", th.primary, actionW, v -> { closeOpen(); actions.onEdit(x); }));
        }
        actionsRow.addView(actionBtn(ctx, "🗑", th.danger, actionW, v -> { closeOpen(); actions.onDelete(x); }));

        View face = buildFace(ctx, th, x);
        wrap.addView(face, new FrameLayout.LayoutParams(-1, -2));

        final float[] startRaw = {0};
        final float[] startTx = {0};
        face.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startRaw[0] = event.getRawX();
                    startTx[0] = v.getTranslationX();
                    if (openCard != null && openCard != v) closeOpen();
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - startRaw[0];
                    if (Math.abs(dx) > dp(ctx, 6)) {
                        float nt = Math.max(-actionsWidth, Math.min(0, startTx[0] + dx));
                        v.setTranslationX(nt);
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean open = v.getTranslationX() < -actionsWidth / 2f;
                    v.animate().translationX(open ? -actionsWidth : 0).setDuration(160).start();
                    openCard = open ? v : null;
                    return false;
                }
            }
            return false;
        });

        return wrap;
    }

    private static void closeOpen() {
        if (openCard != null) {
            openCard.animate().translationX(0).setDuration(160).start();
            openCard = null;
        }
    }

    private static View actionBtn(Activity ctx, String label, int bg, int w, View.OnClickListener l) {
        TextView t = new TextView(ctx);
        t.setText(label); t.setTextSize(18); t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(bg);
        t.setOnClickListener(l);
        t.setLayoutParams(new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
        return t;
    }

    private static View buildFace(Activity ctx, Theme th, Installment x) {
        int accentColor = x.isFinished() ? th.success : (x.isDueSoonOrOverdue() ? th.danger : th.primary);

        LinearLayout row = new LinearLayout(ctx); row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(rounded(ctx, th.card, 14));
        row.setClipToOutline(true);

        View accent = new View(ctx);
        accent.setBackgroundColor(accentColor);
        row.addView(accent, new LinearLayout.LayoutParams(dp(ctx, 5), -1));

        LinearLayout inner = new LinearLayout(ctx); inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));

        Banks.Bank bank = Banks.byId(x.bank);
        if (!bank.id.isEmpty()) {
            TextView badge = new TextView(ctx);
            badge.setText(Banks.initials(bank.name));
            badge.setTextColor(0xFFFFFFFF);
            badge.setTextSize(13);
            badge.setTypeface(null, 1);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable bd = new GradientDrawable();
            bd.setShape(GradientDrawable.OVAL);
            bd.setColor(bank.color);
            badge.setBackground(bd);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dp(ctx, 30), dp(ctx, 30));
            bLp.setMarginStart(dp(ctx, 10));
            inner.addView(badge, bLp);
        }

        LinearLayout text = new LinearLayout(ctx); text.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(ctx); name.setText(x.title); name.setTextSize(17);
        name.setTextColor(th.text); name.setTypeface(null, 1);
        text.addView(name, new LinearLayout.LayoutParams(-2, -2));

        TextView sub = new TextView(ctx);
        sub.setText("قسط " + Math.min(x.paidCount + 1, x.totalCount) + " از " + x.totalCount + "  •  " + money(x.amount));
        sub.setTextSize(14); sub.setTextColor(th.muted);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2); subLp.topMargin = dp(ctx, 2);
        text.addView(sub, subLp);

        TextView due = new TextView(ctx);
        due.setText(x.isFinished() ? "✅ تکمیل شده" : "🗓 سررسید: " + x.dueText());
        due.setTextSize(14); due.setTextColor(x.isFinished() ? th.success : accentColor);
        LinearLayout.LayoutParams dueLp = new LinearLayout.LayoutParams(-2, -2); dueLp.topMargin = dp(ctx, 2);
        text.addView(due, dueLp);

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1);
        textLp.setMarginStart(bank.id.isEmpty() ? 0 : dp(ctx, 2));
        inner.addView(text, textLp);

        // a small hint that the card swipes, since the always-visible buttons are gone
        TextView hint = new TextView(ctx);
        hint.setText("⋮");
        hint.setTextColor(th.muted);
        hint.setTextSize(18);
        inner.addView(hint, new LinearLayout.LayoutParams(-2, -2));

        row.addView(inner, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }
}
