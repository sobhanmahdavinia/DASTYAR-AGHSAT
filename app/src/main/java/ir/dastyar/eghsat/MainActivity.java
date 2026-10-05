package ir.dastyar.eghsat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import java.text.NumberFormat;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout list, root, summaryBox;
    Theme th;
    View fab;
    FrameLayout outer, drawer;
    View scrim;
    boolean drawerOpen = false;
    final int REQ = 90;
    int selectedJYear = -1, selectedJMonth = -1;
    int homeFilter = 2; // 0 همه، 1 پرداخت شده، 2 پرداخت نشده، 3 معوق شده
    boolean filterCustomized = false;
    HorizontalScrollView monthScroller;
    TextView filterStateTop, clearFilterTop;

    int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    TextView tv(String s, float size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); return t;
    }
    GradientDrawable rounded(int color, float radiusDp) { GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radiusDp));return g; }
    GradientDrawable stroke(int color,int border,float radiusDp){GradientDrawable g=rounded(color,radiusDp);g.setStroke(dp(1),border);return g;}
    String money(long n) { return NumberFormat.getInstance(Locale.US).format(n) + " تومان"; }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); th=Theme.get(this); applyBars(); build();
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ);
    }

    void applyBars(){ getWindow().setStatusBarColor(th.bg);getWindow().setNavigationBarColor(th.bg); }

    void build() {
        outer=new FrameLayout(this);outer.setBackgroundColor(th.bg);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(16),dp(16),dp(12));

        // Navigation: menu on the left, compact filter button on the right.
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView menu=tv("☰",23,th.text);menu.setGravity(Gravity.CENTER);menu.setBackground(rounded(th.card,18));
        menu.setContentDescription("منوی برنامه");menu.setOnClickListener(v->openDrawer());
        top.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(44)));
        Space spacer=new Space(this);top.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        ImageButton filterBtn=new ImageButton(this);filterBtn.setImageResource(R.drawable.ic_filter);filterBtn.setColorFilter(th.text);filterBtn.setContentDescription("فیلتر اقساط");
        filterBtn.setPadding(dp(10),dp(10),dp(10),dp(10));filterBtn.setBackground(rounded(th.card,18));filterBtn.setOnClickListener(v->showHomeFilter());
        // Filter status sits immediately to the left of the filter icon.
        filterStateTop=tv("",12,th.text); filterStateTop.setTypeface(null,Typeface.BOLD); filterStateTop.setGravity(Gravity.CENTER);
        filterStateTop.setPadding(dp(10),0,dp(10),0);
        filterStateTop.setBackground(stroke(th.card,th.border,14));
        filterStateTop.setOnClickListener(v->showHomeFilter());
        LinearLayout.LayoutParams stateLp=new LinearLayout.LayoutParams(dp(112),dp(38)); stateLp.setMargins(0,0,dp(5),0); top.addView(filterStateTop,stateLp);
        clearFilterTop=tv("× حذف",11,th.primary); clearFilterTop.setTypeface(null,Typeface.BOLD); clearFilterTop.setGravity(Gravity.CENTER);
        clearFilterTop.setVisibility(filterCustomized?View.VISIBLE:View.GONE); clearFilterTop.setOnClickListener(v->{homeFilter=2;filterCustomized=false;refresh();});
        LinearLayout.LayoutParams clearLp=new LinearLayout.LayoutParams(dp(64),dp(38)); clearLp.setMargins(0,0,dp(5),0); top.addView(clearFilterTop,clearLp);
        top.addView(filterBtn,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout.LayoutParams topLp=new LinearLayout.LayoutParams(-1,-2);topLp.setMargins(0,0,0,dp(12));root.addView(top,topLp);

        summaryBox=new LinearLayout(this);summaryBox.setOrientation(LinearLayout.VERTICAL);summaryBox.setPadding(dp(10),dp(8),dp(10),dp(8));summaryBox.setBackground(rounded(th.primary,24));
        LinearLayout.LayoutParams sLp=new LinearLayout.LayoutParams(-1,-2);sLp.setMargins(0,0,0,dp(18));root.addView(summaryBox,sLp);
        TextView h=tv("اقساط من",18,th.text);h.setTypeface(null,1);LinearLayout.LayoutParams hLp=new LinearLayout.LayoutParams(-1,-2);hLp.setMargins(dp(2),0,dp(2),dp(8));root.addView(h,hLp);

        ScrollView sv=new ScrollView(this);sv.setClipToPadding(false);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(0,0,0,dp(76));sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        outer.addView(root,new FrameLayout.LayoutParams(-1,-1));

        TextView fabBtn=tv("＋  تعریف اقساط",15,th.primaryText);fabBtn.setGravity(Gravity.CENTER);fabBtn.setTypeface(null,1);fabBtn.setBackground(rounded(th.primary,30));fabBtn.setElevation(dp(8));fabBtn.setOnClickListener(v->startActivity(new Intent(this,AddInstallmentActivity.class)));
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(142),dp(58));fp.gravity=Gravity.BOTTOM|Gravity.RIGHT;fp.setMargins(0,0,dp(20),dp(20));outer.addView(fabBtn,fp);fab=fabBtn;

        outer.setOnApplyWindowInsetsListener((v,insets)->{Insets sb=insets.getInsets(WindowInsets.Type.systemBars());root.setPadding(dp(16),sb.top+dp(12),dp(16),dp(12));FrameLayout.LayoutParams flp=(FrameLayout.LayoutParams)fab.getLayoutParams();flp.bottomMargin=sb.bottom+dp(20);fab.setLayoutParams(flp);return insets;});
        setContentView(outer);refresh();
    }

    @Override protected void onResume(){super.onResume();if(list!=null)refresh();}
    @Override public void onBackPressed(){if(drawerOpen){closeDrawer();return;}super.onBackPressed();}

    void openDrawer(){
        if(drawerOpen)return;drawerOpen=true;
        scrim=new View(this);scrim.setBackgroundColor(0x99000000);scrim.setOnClickListener(v->closeDrawer());
        outer.addView(scrim,new FrameLayout.LayoutParams(-1,-1));scrim.bringToFront();
        drawer=new FrameLayout(this);drawer.setBackground(rounded(th.bg,0));drawer.setElevation(dp(18));
        int width=(int)(getResources().getDisplayMetrics().widthPixels*0.68f);
        FrameLayout.LayoutParams dpLp=new FrameLayout.LayoutParams(width,-1);dpLp.gravity=Gravity.LEFT;outer.addView(drawer,dpLp);drawer.bringToFront();
        buildDrawer();drawer.setTranslationX(-width);drawer.animate().translationX(0).setDuration(220).start();
    }

    void closeDrawer(){if(!drawerOpen)return;drawerOpen=false;if(drawer!=null){int w=drawer.getWidth();drawer.animate().translationX(-w).setDuration(180).withEndAction(()->{outer.removeView(drawer);outer.removeView(scrim);drawer=null;scrim=null;}).start();}}

    TextView menuItem(String icon,String label){
        TextView t=tv(icon+"   "+label,15,th.text);t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);t.setPadding(dp(18),0,dp(18),0);t.setBackground(stroke(th.card,th.border,18));return t;
    }
    void addMenuItem(LinearLayout box,String icon,String label,View.OnClickListener click){TextView t=menuItem(icon,label);t.setOnClickListener(v->{closeDrawer();click.onClick(v);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.setMargins(0,0,0,dp(10));box.addView(t,lp);}

    void buildDrawer(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(14),dp(28),dp(14),dp(18));
        int[] headColors;
        if(th.orange) headColors=new int[]{0xFF6B2E13,0xFF9B451D,0xFFD06B2C,0xFFF2B56B};
        else if(th.green) headColors=new int[]{0xFF174A31,0xFF236B47,0xFF3C8A5A,0xFF8BCF9E};
        else if(th.dark) headColors=new int[]{0xFF0B1422,0xFF18263B,0xFF274A78,0xFF4B78B5};
        else if(th.blue) headColors=new int[]{0xFF061B31,0xFF0C3765,0xFF1769AA,0xFF64B5F6};
        else headColors=new int[]{0xFF234A78,0xFF2E659F,0xFF4B86C5,0xFF8DB9E5};
        GradientDrawable headBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,headColors);headBg.setCornerRadius(dp(26));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(18),dp(18),dp(18),dp(18));head.setBackground(headBg);
        TextView ht=tv("منوی اقساط",20,Color.WHITE);ht.setTypeface(null,1);head.addView(ht);
        TextView hs=tv("مدیریت، تقویم، محاسبه و ظاهر برنامه",12,0xE6FFFFFF);hs.setPadding(0,dp(5),0,0);head.addView(hs);
        LinearLayout shades=new LinearLayout(this);shades.setPadding(0,dp(14),0,0);
        int[] blues=th.orange?new int[]{0xFF5D2810,0xFF8D3D18,0xFFB85B25,0xFFD87B35,0xFFE9A45F,0xFFF5C994,0xFFFFE9D6}:th.green?new int[]{0xFF16422C,0xFF215E3D,0xFF2F7A4D,0xFF459B61,0xFF6EBF82,0xFFA4D8B2,0xFFE4F5EA}:th.dark?new int[]{0xFF08111E,0xFF13253B,0xFF1C3858,0xFF2D5A87,0xFF4B78B5,0xFF739AC6,0xFFB2C8DF}:th.blue?new int[]{0xFF061B31,0xFF0C3765,0xFF1769AA,0xFF1976D2,0xFF42A5F5,0xFF90CAF9,0xFFE3F2FD}:new int[]{0xFF234A78,0xFF2E659F,0xFF4B86C5,0xFF6E9DD0,0xFF8DB9E5,0xFFB9D0E8,0xFFE7F0F8};
        for(int c:blues){View v=new View(this);v.setBackground(rounded(c,4));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(6),1);lp.setMargins(dp(2),0,dp(2),0);shades.addView(v,lp);}head.addView(shades);
        panel.addView(head,new LinearLayout.LayoutParams(-1,dp(142)));

        TextView sec=tv("ابزار",13,th.muted);sec.setTypeface(null,1);sec.setPadding(dp(4),dp(18),dp(4),dp(8));panel.addView(sec);
        addMenuItem(panel,"📋","داشبورد مدیریت اقساط",v->startActivity(new Intent(this,AllInstallmentsActivity.class)));
        addMenuItem(panel,"📅","تقویم اقساط",v->startActivity(new Intent(this,CalendarActivity.class)));
        addMenuItem(panel,"🧮","ماشین حساب اقساط",v->startActivity(new Intent(this,InstallmentCalculatorActivity.class)));
        addMenuItem(panel,"＋","تعریف اقساط",v->startActivity(new Intent(this,AddInstallmentActivity.class)));

        TextView themeTitle=tv("ظاهر برنامه",13,th.muted);themeTitle.setTypeface(null,1);themeTitle.setPadding(dp(4),dp(8),dp(4),dp(8));panel.addView(themeTitle);
        // Show every theme without horizontal scrolling, in a compact 2-column grid.
        GridLayout themes=new GridLayout(this);
        themes.setColumnCount(2);
        themes.setRowCount(3);
        themes.setUseDefaultMargins(false);
        addThemeButton(themes,"☀","روشن",Theme.LIGHT);
        addThemeButton(themes,"☾","تاریک",Theme.DARK);
        addThemeButton(themes,"🔵","آبی",Theme.BLUE);
        addThemeButton(themes,"🟠","نارنجی",Theme.ORANGE);
        addThemeButton(themes,"🟢","سبز",Theme.GREEN);
        LinearLayout.LayoutParams themeGridLp=new LinearLayout.LayoutParams(-1,dp(220));
        themeGridLp.setMargins(0,0,0,dp(6));
        panel.addView(themes,themeGridLp);
        drawer.addView(panel,new FrameLayout.LayoutParams(-1,-1));
    }

    void addThemeButton(GridLayout box,String icon,String label,int mode){
        LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setGravity(Gravity.CENTER);b.setPadding(dp(4),dp(6),dp(4),dp(6));
        int base; int txt; int iconColor;
        if(mode==Theme.BLUE){base=0xFF0E3456;txt=Color.WHITE;iconColor=0xFF64B5F6;}
        else if(mode==Theme.DARK){base=0xFF1E2938;txt=Color.WHITE;iconColor=Color.WHITE;}
        else if(mode==Theme.ORANGE){base=0xFFFFE9D6;txt=0xFF7A3E18;iconColor=0xFFD06B2C;}
        else if(mode==Theme.GREEN){base=0xFFE4F5EA;txt=0xFF205A38;iconColor=0xFF3C8A5A;}
        else {base=Color.WHITE;txt=th.text;iconColor=txt;}
        b.setBackground(stroke(base,mode==th.mode?th.primary:th.border,16));TextView i=tv(icon,20,iconColor);i.setGravity(Gravity.CENTER);b.addView(i);TextView l=tv(label,11,txt);l.setGravity(Gravity.CENTER);b.addView(l);
        b.setOnClickListener(v->{Theme.setMode(MainActivity.this,mode);th=Theme.get(MainActivity.this);applyBars();closeDrawer();recreate();});
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
        lp.width=0; lp.height=dp(76); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
        lp.setMargins(dp(3),dp(3),dp(3),dp(3));
        box.addView(b,lp);
    }

    void summaryRow(String label,String value,boolean strong){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);
        TextView l=tv(label,12,th.primaryText);l.setAlpha(.86f);r.addView(l,new LinearLayout.LayoutParams(0,-2,1));
        TextView v=tv(value,strong?13:12,th.primaryText);if(strong)v.setTypeface(null,1);r.addView(v,new LinearLayout.LayoutParams(-2,-2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(3);summaryBox.addView(r,lp);
    }

    int monthKey(int y,int m){ return y*12+(m-1); }
    String monthTitle(int y,int m){ return PersianDate.MONTH_NAMES[m-1]+" "+y; }

    ArrayList<int[]> homeMonths(int[] today){
        ArrayList<int[]> out=new ArrayList<>();
        int current=monthKey(today[0],today[1]);
        int start=current-6;
        int end=current+24;
        for(int k=start;k<=end;k++){ int y=k/12; int m=k%12+1; out.add(new int[]{y,m}); }
        return out;
    }

    void renderMonthStrip(int[] today){
        monthScroller=new HorizontalScrollView(this);
        monthScroller.setHorizontalScrollBarEnabled(false);
        monthScroller.setFillViewport(false);
        // RTL gives the user the natural Persian interaction: future months are
        // physically on the left and dragging left reveals later months.
        monthScroller.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout strip=new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ArrayList<int[]> months=homeMonths(today);
        int selectedIndex=0;
        for(int i=0;i<months.size();i++){
            int[] jm=months.get(i);
            if(jm[0]==selectedJYear&&jm[1]==selectedJMonth) selectedIndex=i;
            final int fy=jm[0], fm=jm[1];
            boolean selected=fy==selectedJYear&&fm==selectedJMonth;
            TextView chip=tv(monthTitle(fy,fm),12,selected?th.primaryText:th.text);
            chip.setGravity(Gravity.CENTER); chip.setTypeface(null,Typeface.BOLD);
            chip.setBackground(selected?rounded(thisColor(th.primary),16):stroke(th.card,th.border,16));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(108),dp(42));
            lp.setMargins(dp(3),0,dp(3),0); strip.addView(chip,lp);
            chip.setOnClickListener(v->{selectedJYear=fy;selectedJMonth=fm;refresh();});
        }
        monthScroller.addView(strip,new HorizontalScrollView.LayoutParams(-2,dp(44)));
        summaryBox.addView(monthScroller,new LinearLayout.LayoutParams(-1,dp(48)));
        final int si=selectedIndex;
        monthScroller.post(()->{
            int max=Math.max(0,strip.getWidth()-monthScroller.getWidth());
            int itemW=dp(114);
            int target=Math.max(0,max-(si*itemW));
            monthScroller.scrollTo(target,0);
        });
    }
    int thisColor(int c){ return c; }

    void showHomeFilter(){
        final String[] options={"همه","پرداخت شده","پرداخت نشده","معوق شده"};
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(10),dp(18),dp(4));
        TextView title=tv("فیلتر اقساط",18,th.text); title.setTypeface(null,1); title.setGravity(Gravity.RIGHT); box.addView(title,new LinearLayout.LayoutParams(-1,dp(34)));
        RadioGroup group=new RadioGroup(this); group.setOrientation(RadioGroup.VERTICAL);
        for(int i=0;i<options.length;i++){
            final int idx=i; RadioButton rb=new RadioButton(this); rb.setText(options[i]); rb.setTextColor(th.text); rb.setButtonTintList(android.content.res.ColorStateList.valueOf(th.primary)); rb.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); rb.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); rb.setPadding(0,0,0,0); rb.setChecked(homeFilter==i); group.addView(rb,new RadioGroup.LayoutParams(-1,dp(46)));
        }
        box.addView(group);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(box).setNegativeButton("انصراف",null).setPositiveButton("نمایش",null).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(th.primary);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(th.muted);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{homeFilter=group.indexOfChild(group.findViewById(group.getCheckedRadioButtonId())); filterCustomized=true; dialog.dismiss(); refresh();});
        });
        dialog.getWindow(); dialog.show();
        android.view.Window w=dialog.getWindow(); if(w!=null){w.setBackgroundDrawable(rounded(th.card,24));}
    }

    void refresh(){
        list.removeAllViews(); List<Installment> all=Store.all(this);
        Calendar now=Calendar.getInstance();int[] todayJ=PersianDate.toJalali(now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1,now.get(Calendar.DAY_OF_MONTH));
        if(selectedJYear<1380 || selectedJMonth<1){ selectedJYear=todayJ[0]; selectedJMonth=todayJ[1]; }

        summaryBox.removeAllViews();
        if(filterStateTop!=null){
            String[] fs={"همه","پرداخت شده","پرداخت نشده","معوق شده"};
            filterStateTop.setText("فیلتر: "+fs[Math.max(0,Math.min(homeFilter,3))]);
            filterStateTop.setVisibility(View.VISIBLE);
            if(clearFilterTop!=null) clearFilterTop.setVisibility(filterCustomized?View.VISIBLE:View.GONE);
        }
        renderMonthStrip(todayJ);
        long totalThisMonth=0,paidThisMonth=0,remainingThisMonth=0; int overdueCount=0,monthCount=0;
        for(Installment x:all){
            int idx=x.paymentIndexForMonth(selectedJYear,selectedJMonth); if(idx<0) continue;
            monthCount++; totalThisMonth+=x.amount;
            if(idx<x.paidCount) paidThisMonth+=x.amount; else {remainingThisMonth+=x.amount; long due=x.dueMillisForIndex(idx); if(due<System.currentTimeMillis()) overdueCount++;}
        }
        long totalDebt=0;int openCount=0;for(Installment x:all)if(!x.isFinished()){totalDebt+=x.amount*(x.totalCount-x.paidCount);openCount++;}
        TextView head=tv("📆  "+monthTitle(selectedJYear,selectedJMonth),14,th.primaryText);head.setTypeface(null,1);head.setGravity(Gravity.RIGHT);summaryBox.addView(head,new LinearLayout.LayoutParams(-1,dp(28)));
        summaryRow("کل اقساط",money(totalThisMonth),false);summaryRow("پرداخت‌شده",money(paidThisMonth),false);summaryRow("باقی‌مانده",money(remainingThisMonth),true);summaryRow("بدهی کل",money(totalDebt)+"  ("+openCount+" فعال)",false);
        List<Installment> visible=new ArrayList<>(); List<Integer> indices=new ArrayList<>();
        boolean selectedCurrent=selectedJYear==todayJ[0]&&selectedJMonth==todayJ[1];
        for(Installment x:all){
            int idx=x.paymentIndexForMonth(selectedJYear,selectedJMonth); if(idx<0) continue;
            boolean paid=idx<x.paidCount; long due=x.dueMillisForIndex(idx); boolean overdue=!paid&&due<System.currentTimeMillis();
            boolean ok=homeFilter==0 || (homeFilter==1&&paid) || (homeFilter==2&&!paid) || (homeFilter==3&&overdue);
            if(ok){visible.add(x);indices.add(idx);}
        }
        ArrayList<Integer> order=new ArrayList<>();for(int i=0;i<visible.size();i++)order.add(i);
        Collections.sort(order,(a,b)->Long.compare(visible.get(a).dueMillisForIndex(indices.get(a)),visible.get(b).dueMillisForIndex(indices.get(b))));
        if(visible.isEmpty()){
            TextView e=tv(monthCount==0?"در این ماه قسطی برنامه‌ریزی نشده.":"قسطی با این فیلتر پیدا نشد.",16,th.muted);e.setGravity(Gravity.CENTER);list.addView(e,new LinearLayout.LayoutParams(-1,dp(180)));return;
        }
        for(int oi:order){ Installment x=visible.get(oi); int idx=indices.get(oi); boolean canPay=selectedCurrent && idx==x.paidCount && !x.isFinished();
            list.addView(CardBuilder.buildForMonth(this,th,x,new CardBuilder.Actions(){public void onPay(Installment y){confirmPay(y);}public void onEdit(Installment y){startActivity(new Intent(MainActivity.this,AddInstallmentActivity.class).putExtra("id",y.id));}public void onDelete(Installment y){confirmDelete(y);}public void onReminderChanged(Installment y){refresh();}},idx,true,canPay));
        }
    }

    void confirmPay(Installment x){int n=Math.min(x.paidCount+1,x.totalCount);new AlertDialog.Builder(this).setTitle("تأیید پرداخت قسط").setMessage("از پرداخت قسط "+n+" از "+x.totalCount+" برای «"+x.title+"» به مبلغ "+money(x.amount)+" مطمئنی؟").setPositiveButton("بله، پرداخت شد",(d,w)->{x.paidCount++;if(x.paidCount>=x.totalCount)x.active=false;Store.upsert(this,x);PaymentLog.record(this,x.id,x.amount);AlarmHelper.schedule(this,x);refresh();}).setNegativeButton("انصراف",null).show();}
    void confirmDelete(Installment x){
        new AlertDialog.Builder(this).setTitle("حذف قسط").setMessage("از حذف «"+x.title+"» مطمئنید؟").setPositiveButton("بله",(d,w)->
                new AlertDialog.Builder(this).setTitle("تأیید نهایی حذف").setMessage("این کار بدون بازگشت است. آیا انجام شود؟").setPositiveButton("بله، حذف کن",(d2,w2)->{AlarmHelper.cancel(this,x.id);Store.delete(this,x.id);PaymentLog.deleteForInstallment(this,x.id);refresh();}).setNegativeButton("خیر",null).show()
        ).setNegativeButton("خیر",null).show();
    }
}
