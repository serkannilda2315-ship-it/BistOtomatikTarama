package com.example.bisttarama;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private EditText baseUrlInput, apiKeyInput;
    private Spinner timeframeSpinner;
    private CheckBox autoCheck;
    private Button scanButton;
    private TextView statusText, resultsText;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private volatile boolean scanning = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoScanRunnable = new Runnable() { @Override public void run() { if (autoCheck != null && autoCheck.isChecked()) { startScan(); mainHandler.postDelayed(this, 300000L); } } };

    static class Bar {
        double open, high, low, close, volume;
        String time;
    }
    static class Signal {
        String symbol, time;
        double price, volRatio;
        Signal(String s, String t, double p, double r) { symbol=s; time=t; price=p; volRatio=r; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(16),dp(16),dp(20)); root.setBackgroundColor(Color.rgb(16,24,32));
        scroll.addView(root);
        TextView title = text("BIST OTOMATİK TARAMA", 23, "#7CFC8A", true); root.addView(title);
        TextView subtitle = text("RSI 14 • Kompozit RSI • EMA14/34 • WT 10/21/4 • Hacim 3x", 13, "#CFD8DC", false); subtitle.setPadding(0,dp(6),0,dp(14)); root.addView(subtitle);

        root.addView(text("Veri API adresi (sunucu adresini gir)", 13, "#FFFFFF", true));
        baseUrlInput = new EditText(this); baseUrlInput.setSingleLine(true); baseUrlInput.setText("https://SUNUCU-ADRESINIZ"); baseUrlInput.setHint("https://..."); styleInput(baseUrlInput); root.addView(baseUrlInput);
        root.addView(text("API anahtarı (sunucuda gerekiyorsa)", 13, "#FFFFFF", true));
        apiKeyInput = new EditText(this); apiKeyInput.setSingleLine(true); apiKeyInput.setHint("X-API-Key"); styleInput(apiKeyInput); root.addView(apiKeyInput);
        root.addView(text("Tarama zaman dilimi", 13, "#FFFFFF", true));
        timeframeSpinner = new Spinner(this);
        String[] tfs = {"1 dakika", "5 dakika", "15 dakika", "30 dakika", "1 saat", "Günlük"};
        timeframeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, tfs)); root.addView(timeframeSpinner);
        autoCheck = new CheckBox(this); autoCheck.setText("Uygulama açıkken otomatik yenile (5 dk)"); autoCheck.setTextColor(Color.WHITE); root.addView(autoCheck);
        scanButton = new Button(this); scanButton.setText("TÜM BIST'İ TARA"); root.addView(scanButton);
        statusText = text("Hazır. Önce çalışan veri API adresi girilmelidir.", 13, "#FFD166", false); statusText.setPadding(0,dp(12),0,dp(10)); root.addView(statusText);
        root.addView(text("AL SİNYALLERİ", 18, "#7CFC8A", true));
        resultsText = text("Henüz tarama yapılmadı.", 14, "#FFFFFF", false); resultsText.setPadding(0,dp(8),0,0); root.addView(resultsText);
        setContentView(scroll);
        scanButton.setOnClickListener(v -> startScan());
        autoCheck.setOnCheckedChangeListener((b, checked) -> {
            mainHandler.removeCallbacks(autoScanRunnable);
            if (checked) { statusText.setText("Otomatik yenileme açık: uygulama ekrandayken yaklaşık 5 dakikada bir tarama yapılır."); startScan(); mainHandler.postDelayed(autoScanRunnable, 300000L); }
        });
    }

    private void styleInput(EditText e) { e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); e.setSingleLine(true); }
    private TextView text(String s, int size, String color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(Color.parseColor(color)); if (bold) v.setTypeface(null, Typeface.BOLD); return v; }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }

    private String interval() {
        switch (timeframeSpinner.getSelectedItemPosition()) {
            case 0: return "1m"; case 1: return "5m"; case 2: return "15m"; case 3: return "30m"; case 4: return "60m"; default: return "1d";
        }
    }
    private String period() { String tf=interval(); if (tf.equals("1d")) return "3mo"; if (tf.equals("60m") || tf.equals("30m") || tf.equals("15m")) return "1mo"; return "5d"; }
    private String base() { String s=baseUrlInput.getText().toString().trim(); while (s.endsWith("/")) s=s.substring(0,s.length()-1); return s; }
    private String apiKey() { return apiKeyInput.getText().toString().trim(); }

    private void startScan() {
        if (scanning) return;
        String b = base();
        if (b.isEmpty() || b.contains("SUNUCU-ADRESINIZ")) { statusText.setText("Önce veri servisini internete açık şekilde kurup API adresini girmen gerekiyor. ZIP içindeki KURULUM.md dosyasına bak."); return; }
        scanning = true; scanButton.setEnabled(false); statusText.setText("Hisse listesi ve mum verileri alınıyor..."); resultsText.setText("Tarama sürüyor...");
        pool.execute(() -> {
            try {
                List<String> symbols = fetchSymbols(b);
                List<Bar> indexBars = fetchBars(b, "XU100", interval(), period());
                if (symbols.isEmpty()) throw new Exception("/symbols yanıtında hisse listesi bulunamadı.");
                List<Signal> signals = Collections.synchronizedList(new ArrayList<>());
                AtomicInteger done = new AtomicInteger(0);
                List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
                for (String symbol : symbols) {
                    tasks.add(pool.submit(() -> {
                        try {
                            List<Bar> bars = fetchBars(b, symbol, interval(), period());
                            Signal s = evaluate(symbol, bars, indexBars);
                            if (s != null) signals.add(s);
                        } catch (Exception ignored) { }
                        int n=done.incrementAndGet();
                        if (n % 10 == 0 || n == symbols.size()) runOnUiThread(() -> statusText.setText("Taranan: " + n + "/" + symbols.size() + " • AL: " + signals.size()));
                    }));
                }
                for (java.util.concurrent.Future<?> f : tasks) f.get();
                Collections.sort(signals, (a,c) -> c.time.compareTo(a.time));
                StringBuilder out = new StringBuilder();
                out.append(String.format(Locale.US,"%-10s %-12s %-12s %s\n", "HİSSE", "FİYAT", "HACİM ORANI", "MUM ZAMANI"));
                out.append("------------------------------------------------\n");
                for (Signal s : signals) out.append(String.format(Locale.US,"%-10s %-12.3f %-12.2fx %s\n", s.symbol, s.price, s.volRatio, s.time));
                if (signals.isEmpty()) out.append("Bu taramada tüm şartları aynı mumda sağlayan hisse bulunamadı.\n");
                final String result = out.toString();
                runOnUiThread(() -> { resultsText.setText(result); statusText.setText("Tamamlandı • " + symbols.size() + " hisse • AL: " + signals.size() + " • Veri gecikmeli olabilir."); scanButton.setEnabled(true); scanning=false; });
            } catch (Exception ex) {
                runOnUiThread(() -> { statusText.setText("Hata: " + ex.getMessage()); resultsText.setText("API adresini, erişimi ve API anahtarını kontrol et."); scanButton.setEnabled(true); scanning=false; });
            }
        });
    }

    private String get(String urlString) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(urlString).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(20000); c.setRequestMethod("GET"); c.setRequestProperty("Accept","application/json");
        String key=apiKey(); if (!key.isEmpty()) c.setRequestProperty("X-API-Key",key);
        int code=c.getResponseCode(); InputStream in=(code>=200 && code<300)?c.getInputStream():c.getErrorStream();
        BufferedReader r=new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)); StringBuilder sb=new StringBuilder(); String line; while((line=r.readLine())!=null) sb.append(line); r.close(); c.disconnect();
        if(code<200 || code>=300) throw new Exception("HTTP " + code + " — " + sb.toString()); return sb.toString();
    }
    private List<String> fetchSymbols(String b) throws Exception {
        JSONObject j=new JSONObject(get(b+"/symbols")); JSONArray arr=null;
        Object s=j.opt("symbols"); if(s instanceof JSONArray) arr=(JSONArray)s;
        if(arr==null && j.opt("items") instanceof JSONArray) arr=j.optJSONArray("items");
        if(arr==null && j.opt("data") instanceof JSONArray) arr=j.optJSONArray("data");
        List<String> out=new ArrayList<>(); if(arr!=null) for(int i=0;i<arr.length();i++) { Object o=arr.get(i); String sym=o instanceof JSONObject?((JSONObject)o).optString("symbol", ((JSONObject)o).optString("ticker","")):String.valueOf(o); sym=sym.trim().toUpperCase(Locale.ROOT); if(sym.matches("[A-Z0-9]{2,10}") && !sym.equals("XU100")) out.add(sym); }
        return out;
    }
    private List<Bar> fetchBars(String b, String symbol, String tf, String p) throws Exception {
        String u=b+"/history/"+URLEncoder.encode(symbol, "UTF-8")+"?period="+p+"&interval="+tf;
        JSONObject j=new JSONObject(get(u)); JSONArray a=j.optJSONArray("bars"); List<Bar> out=new ArrayList<>(); if(a==null)return out;
        for(int i=0;i<a.length();i++){ JSONObject x=a.optJSONObject(i); if(x==null)continue; Bar z=new Bar(); z.open=num(x,"open"); z.high=num(x,"high"); z.low=num(x,"low"); z.close=num(x,"close"); z.volume=num(x,"volume"); z.time=x.optString("time",String.valueOf(i)); if(z.close>0 && z.high>0 && z.low>0)out.add(z); }
        return out;
    }
    private double num(JSONObject j,String k) { Object o=j.opt(k); if(o==null || o==JSONObject.NULL)return 0; try{return Double.parseDouble(String.valueOf(o));}catch(Exception e){return 0;} }

    private Signal evaluate(String symbol, List<Bar> bars, List<Bar> idx) {
        int n=Math.min(bars.size(),idx.size()); if(n<60)return null;
        int offB=bars.size()-n, offI=idx.size()-n;
        double[] close=new double[n], ratio=new double[n], rsi=new double[n], crsi=new double[n], ema14=new double[n], ema34=new double[n], wt1=new double[n], wt2=new double[n], volSma=new double[n];
        for(int i=0;i<n;i++){ Bar b=bars.get(offB+i); Bar x=idx.get(offI+i); if(x.close<=0)return null; close[i]=b.close; ratio[i]=b.close/x.close; }
        rsi=calcRsi(close,14); crsi=calcRsi(ratio,14); ema14=ema(ratio,14); ema34=ema(ratio,34);
        double[] typical=new double[n], esa= new double[n], absDev=new double[n], devEma=new double[n], ci=new double[n];
        for(int i=0;i<n;i++) typical[i]=(bars.get(offB+i).high+bars.get(offB+i).low+bars.get(offB+i).close)/3.0;
        esa=ema(typical,10); for(int i=0;i<n;i++) absDev[i]=Math.abs(typical[i]-esa[i]); devEma=ema(absDev,10);
        for(int i=0;i<n;i++) ci[i]=devEma[i]==0?0:(typical[i]-esa[i])/(0.015*devEma[i]);
        wt1=ema(ci,21); wt2=sma(wt1,4);
        for(int i=0;i<n;i++){double sum=0; int from=Math.max(0,i-19); for(int k=from;k<=i;k++)sum+=bars.get(offB+k).volume; volSma[i]=sum/(i-from+1);}
        int i=n-1, p=i-1; if(p<1)return null;
        boolean rsiCross=rsi[p]<=50 && rsi[i]>50;
        boolean compRsiCross=crsi[p]<=50 && crsi[i]>50;
        boolean compEmaCross=(ratio[p]<=ema14[p] && ratio[i]>ema14[i]) || (ratio[p]<=ema34[p] && ratio[i]>ema34[i]);
        boolean wtCross=wt1[p]<=wt2[p] && wt1[i]>wt2[i];
        double vr=volSma[i]>0?bars.get(offB+i).volume/volSma[i]:0;
        boolean volume=vr>=3.0;
        if(rsiCross && compRsiCross && compEmaCross && wtCross && volume) return new Signal(symbol,bars.get(bars.size()-1).time,bars.get(bars.size()-1).close,vr);
        return null;
    }
    private double[] ema(double[] x,int len){double[] y=new double[x.length]; if(x.length==0)return y; double a=2.0/(len+1.0); y[0]=x[0]; for(int i=1;i<x.length;i++)y[i]=a*x[i]+(1-a)*y[i-1]; return y;}
    private double[] sma(double[] x,int len){double[] y=new double[x.length]; for(int i=0;i<x.length;i++){double s=0;int n=0;for(int k=Math.max(0,i-len+1);k<=i;k++){s+=x[k];n++;}y[i]=n>0?s/n:0;}return y;}
    private double[] calcRsi(double[] x,int len){double[] out=new double[x.length]; if(x.length<=len)return out; double gain=0,loss=0; for(int i=1;i<=len;i++){double d=x[i]-x[i-1];if(d>0)gain+=d;else loss-=d;} gain/=len;loss/=len;out[len]=loss==0?100:100-100/(1+gain/loss); for(int i=len+1;i<x.length;i++){double d=x[i]-x[i-1];gain=(gain*(len-1)+Math.max(d,0))/len;loss=(loss*(len-1)+Math.max(-d,0))/len;out[i]=loss==0?100:100-100/(1+gain/loss);}return out;}
}
