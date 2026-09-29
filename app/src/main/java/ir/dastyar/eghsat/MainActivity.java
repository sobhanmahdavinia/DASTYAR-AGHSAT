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

        // Navigation lives in one compact menu button.
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        TextView menu=tv("☰",23,th.text);menu.setGravity(Gravity.CENTER);menu.setBackground(rounded(th.card,18));
        menu.setContentDescription("منوی برنامه");menu.setOnClickListener(v->openDrawer());
        top.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(44)));
        LinearLayout.LayoutParams topLp=new LinearLayout.LayoutParams(-1,-2);topLp.setMargins(0,0,0,dp(12));root.addView(top,topLp);

        summaryBox=new LinearLayout(this);summaryBox.setOrientation(LinearLayout.VERTICAL);summaryBox.setPadding(dp(16),dp(16),dp(16),dp(16));summaryBox.setBackground(rounded(th.primary,24));
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

    void summaryRow(String label,String value,boolean strong){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);TextView l=tv(label,14,th.primaryText);l.setAlpha(.86f);r.addView(l,new LinearLayout.LayoutParams(0,-2,1));TextView v=tv(value,strong?16:14,th.primaryText);if(strong)v.setTypeface(null,1);r.addView(v,new LinearLayout.LayoutParams(-2,-2));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);summaryBox.addView(r,lp);}

    void refresh(){
        list.removeAllViews();List<Installment> all=Store.all(this);
        Calendar now=Calendar.getInstance();int[] todayJ=PersianDate.toJalali(now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1,now.get(Calendar.DAY_OF_MONTH));
        Map<Long,Long> dueThisMonth=new LinkedHashMap<>();
        for(Installment x:all)if(!x.isFinished()){Calendar c=Calendar.getInstance();c.setTimeInMillis(x.nextDueMillis());int[] j=PersianDate.toJalali(c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));if(j[0]==todayJ[0]&&j[1]==todayJ[1])dueThisMonth.put(x.id,x.amount);}
        long paidThisMonth=0;for(PaymentLog.Event e:PaymentLog.all(this)){Calendar c=Calendar.getInstance();c.setTimeInMillis(e.timestamp);int[] j=PersianDate.toJalali(c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));if(j[0]==todayJ[0]&&j[1]==todayJ[1]){paidThisMonth+=e.amount;dueThisMonth.put(e.installmentId,e.amount);}}
        long totalThisMonth=0;for(long v:dueThisMonth.values())totalThisMonth+=v;long remainingThisMonth=Math.max(0,totalThisMonth-paidThisMonth);
        long totalDebt=0;int openCount=0;for(Installment x:all)if(!x.isFinished()){totalDebt+=x.amount*(x.totalCount-x.paidCount);openCount++;}
        summaryBox.removeAllViews();TextView head=tv("📆  خلاصه‌ی "+PersianDate.MONTH_NAMES[todayJ[1]-1],17,th.primaryText);head.setTypeface(null,1);summaryBox.addView(head);
        summaryRow("کل این ماه",money(totalThisMonth),false);summaryRow("پرداخت‌شده",money(paidThisMonth),false);summaryRow("باقی‌مانده این ماه",money(remainingThisMonth),true);summaryRow("مانده کل بدهی",money(totalDebt)+"  ("+openCount+" قسط فعال)",false);
        List<Installment> visible=new ArrayList<>();for(Installment x:all)if(x.isVisibleOnHome())visible.add(x);
        if(visible.isEmpty()){TextView e=tv(all.isEmpty()?"هنوز قسطی ثبت نکرده‌ای.\nاز منوی بالا یا دکمه + شروع کن.":"الان قسطی نزدیک سررسید نیست 👌",16,th.muted);e.setGravity(Gravity.CENTER);list.addView(e,new LinearLayout.LayoutParams(-1,dp(180)));return;}
        Collections.sort(visible,(a,b)->Long.compare(a.nextDueMillis(),b.nextDueMillis()));
        for(Installment x:visible)list.addView(CardBuilder.build(this,th,x,new CardBuilder.Actions(){public void onPay(Installment y){confirmPay(y);}public void onEdit(Installment y){startActivity(new Intent(MainActivity.this,AddInstallmentActivity.class).putExtra("id",y.id));}public void onDelete(Installment y){confirmDelete(y);}}));
    }

    void confirmPay(Installment x){int n=Math.min(x.paidCount+1,x.totalCount);new AlertDialog.Builder(this).setTitle("تأیید پرداخت قسط").setMessage("از پرداخت قسط "+n+" از "+x.totalCount+" برای «"+x.title+"» به مبلغ "+money(x.amount)+" مطمئنی؟").setPositiveButton("بله، پرداخت شد",(d,w)->{x.paidCount++;if(x.paidCount>=x.totalCount)x.active=false;Store.upsert(this,x);PaymentLog.record(this,x.id,x.amount);AlarmHelper.schedule(this,x);refresh();}).setNegativeButton("انصراف",null).show();}
    void confirmDelete(Installment x){new AlertDialog.Builder(this).setTitle("حذف قسط").setMessage("«"+x.title+"» حذف بشه؟ این کار قابل بازگشت نیست.").setPositiveButton("حذف",(d,w)->{AlarmHelper.cancel(this,x.id);Store.delete(this,x.id);PaymentLog.deleteForInstallment(this,x.id);refresh();}).setNegativeButton("انصراف",null).show();}
}
