package com.luna.phonemiccam;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.graphics.ImageFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.util.Size;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class MainActivity extends Activity {
    private final int bg=0xff080d1c, panel=0xff111a2c, panel2=0xff18243a, cyan=0xff24d9eb, white=0xfff2f6ff, muted=0xff98a7c2, green=0xff26e8b3;
    private LinearLayout root;
    private TextView status, address, micButton, micCaption, cameraButton, cameraSwitch, clientState, streamLed;
    private EditText widthField, heightField;
    private StreamServer server;
    private volatile boolean live=false, cameraOn=false, frontCamera=true;
    private volatile int gain=100;
    private AudioRecord recorder;
    private Thread audioThread;
    private WifiManager.WifiLock wifiLock;
    private CameraDevice camera;
    private CameraCaptureSession captureSession;
    private int cameraGeneration=0;
    private ImageReader imageReader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private final Handler ui = new Handler();

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable rounded(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private TextView centered(String s,int size,int color){TextView t=text(s,size,color);t.setGravity(Gravity.CENTER);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams full(int h){return new LinearLayout.LayoutParams(-1,dp(h));}
    private void gap(int h){View v=new View(this);root.addView(v,full(h));}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(rounded(panel,0xff283650,20));return c;}
    private TextView action(String s,boolean primary){TextView t=centered(s,14,primary?bg:white);t.setTypeface(null,Typeface.BOLD);t.setLetterSpacing(.04f);t.setBackground(rounded(primary?cyan:panel2,primary?cyan:0xff35435f,16));return t;}

    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(0xff060912);getWindow().getDecorView().setSystemUiVisibility(0);buildUi();}
    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        root=column();root.setPadding(dp(22),dp(16),dp(22),dp(24));root.setBackgroundColor(bg);scroll.addView(root);setContentView(scroll);
        LinearLayout top=horizontal();TextView mark=centered("L",22,bg);mark.setTypeface(null,Typeface.BOLD);mark.setBackground(rounded(cyan,0,16));LinearLayout.LayoutParams markp=new LinearLayout.LayoutParams(dp(42),dp(42));top.addView(mark,markp);
        LinearLayout names=column();names.setPadding(dp(11),0,0,0);TextView title=text("LUNA",21,white);title.setTypeface(null,Typeface.BOLD);title.setLetterSpacing(.16f);names.addView(title);TextView sub=text("PHONE MICCAM  ·  FOR OBS STUDIO",10,muted);sub.setLetterSpacing(.12f);names.addView(sub);top.addView(names,new LinearLayout.LayoutParams(0,-2,1));status=centered("●  OFFLINE",11,muted);status.setPadding(dp(12),dp(7),dp(12),dp(7));status.setBackground(rounded(panel2,0xff34415a,40));top.addView(status);root.addView(top,full(52));
        gap(20);
        TextView eyebrow=text("YOUR PHONE. YOUR STUDIO.",11,cyan);eyebrow.setTypeface(null,Typeface.BOLD);eyebrow.setLetterSpacing(.14f);root.addView(eyebrow);
        TextView headline=text("Sound clear.\nStay in the moment.",30,white);headline.setTypeface(null,Typeface.BOLD);headline.setLineSpacing(dp(2),1f);root.addView(headline,full(86));
        TextView intro=text("A direct, low-latency mic and camera feed for OBS. Everything stays on your Wi‑Fi.",14,muted);intro.setLineSpacing(dp(3),1f);root.addView(intro,full(52));
        gap(12);
        LinearLayout streamCard=card();LinearLayout row=horizontal();streamLed=centered("●",16,live?green:0xff64718a);streamLed.setBackground(rounded(0xff1d2b40,0,40));row.addView(streamLed,new LinearLayout.LayoutParams(dp(38),dp(38)));LinearLayout streamCopy=column();streamCopy.setPadding(dp(11),0,0,0);TextView label=text("LOCAL STUDIO LINK",10,muted);label.setLetterSpacing(.13f);streamCopy.addView(label);address=text(live?hostUrl():"Start the mic to create your OBS link",15,cyan);address.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);streamCopy.addView(address);row.addView(streamCopy,new LinearLayout.LayoutParams(0,-2,1));TextView copy=centered("COPY",11,cyan);copy.setTypeface(null,Typeface.BOLD);copy.setOnClickListener(v->copySource("audio"));row.addView(copy);streamCard.addView(row);TextView divider=text("Audio uses a direct PCM WebSocket stream — no long WAV buffering.",11,muted);divider.setPadding(0,dp(13),0,0);streamCard.addView(divider);root.addView(streamCard,full(104));
        gap(14);
        LinearLayout controls=horizontal();LinearLayout micBox=column();micBox.setGravity(Gravity.CENTER);micButton=centered(live?"■":"◖",35,live?0xffff6680:cyan);GradientDrawable disc=new GradientDrawable();disc.setShape(GradientDrawable.OVAL);disc.setColor(panel2);disc.setStroke(dp(2),live?0xffff6680:0xff2e8193);micButton.setBackground(disc);micButton.setOnClickListener(v->{if(live)stopStream();else requestMic();});micBox.addView(micButton,new LinearLayout.LayoutParams(dp(108),dp(108)));micCaption=centered(live?"STOP MIC":"START MIC",11,live?0xffff8391:muted);micCaption.setPadding(0,dp(8),0,0);micBox.addView(micCaption);controls.addView(micBox,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout camBox=column();camBox.setGravity(Gravity.CENTER);cameraButton=action(cameraOn?"CAMERA LIVE":"ENABLE CAMERA",false);cameraButton.setOnClickListener(v->{if(cameraOn)stopCamera();else requestCamera();});camBox.addView(cameraButton,full(48));cameraSwitch=action(frontCamera?"FRONT CAMERA":"BACK CAMERA",false);cameraSwitch.setTextSize(11);cameraSwitch.setOnClickListener(v->{frontCamera=!frontCamera;cameraSwitch.setText(frontCamera?"FRONT CAMERA":"BACK CAMERA");if(cameraOn){stopCamera();ui.postDelayed(this::startCamera,350);}});LinearLayout.LayoutParams swp=full(42);swp.setMargins(0,dp(10),0,0);camBox.addView(cameraSwitch,swp);controls.addView(camBox,new LinearLayout.LayoutParams(0,-2,1));root.addView(controls,full(150));
        gap(8);
        LinearLayout gainCard=card();LinearLayout gainRow=horizontal();TextView gainTitle=text("MIC GAIN",11,cyan);gainTitle.setTypeface(null,Typeface.BOLD);gainTitle.setLetterSpacing(.11f);gainRow.addView(gainTitle,new LinearLayout.LayoutParams(0,dp(24),1));TextView gainValue=text("100%",12,white);gainRow.addView(gainValue);gainCard.addView(gainRow);SeekBar gainBar=new SeekBar(this);gainBar.setMax(150);gainBar.setProgress(100);gainBar.setProgressTintList(android.content.res.ColorStateList.valueOf(cyan));gainBar.setThumbTintList(android.content.res.ColorStateList.valueOf(cyan));gainBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean fromUser){gain=value;gainValue.setText(value+"%");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});gainCard.addView(gainBar);root.addView(gainCard,full(68));
        gap(12);
        LinearLayout sizeCard=card();TextView sizeTitle=text("OBS BROWSER SOURCE SIZE",11,cyan);sizeTitle.setTypeface(null,Typeface.BOLD);sizeTitle.setLetterSpacing(.11f);sizeCard.addView(sizeTitle);
        LinearLayout dimensions=horizontal();widthField=numberField("1280");heightField=numberField("720");dimensions.addView(widthField,new LinearLayout.LayoutParams(0,dp(48),1));TextView cross=centered("×",20,muted);dimensions.addView(cross,new LinearLayout.LayoutParams(dp(34),dp(48)));dimensions.addView(heightField,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams dimp=full(58);dimp.setMargins(0,dp(7),0,0);sizeCard.addView(dimensions,dimp);
        LinearLayout presets=horizontal();TextView p720=action("1280 × 720",false),p1080=action("1920 × 1080",false);p720.setOnClickListener(v->setSize(1280,720));p1080.setOnClickListener(v->setSize(1920,1080));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(38),1);pp.setMargins(0,dp(4),dp(6),0);presets.addView(p720,pp);LinearLayout.LayoutParams pp2=new LinearLayout.LayoutParams(0,dp(38),1);pp2.setMargins(dp(6),dp(4),0,0);presets.addView(p1080,pp2);sizeCard.addView(presets);TextView sizeHelp=text("Enter these same pixel values in OBS → Browser Source → Width and Height.",11,muted);sizeHelp.setPadding(0,dp(9),0,0);sizeCard.addView(sizeHelp);root.addView(sizeCard,full(166));
        gap(14);
        LinearLayout copies=horizontal();TextView audioCopy=action("COPY AUDIO SOURCE",true);audioCopy.setOnClickListener(v->copySource("audio"));copies.addView(audioCopy,new LinearLayout.LayoutParams(0,dp(50),1));TextView videoCopy=action("COPY CAMERA SOURCE",false);videoCopy.setOnClickListener(v->copySource("camera"));LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(0,dp(50),1);vp.setMargins(dp(8),0,0,0);copies.addView(videoCopy,vp);root.addView(copies);
        clientState=centered("PRIVATE TO YOUR LOCAL NETWORK  ·  NO ADS  ·  NO CLOUD",9,0xff73829d);clientState.setLetterSpacing(.08f);root.addView(clientState,full(42));
        TextView guideTitle=text("GET IT INTO OBS",12,cyan);guideTitle.setTypeface(null,Typeface.BOLD);guideTitle.setLetterSpacing(.13f);root.addView(guideTitle,full(28));
        TextView guide=text("1  Start the mic above and copy the Audio Source URL. Keep Luna Phone MicCam open while streaming.\n2  In OBS, add a Browser Source and paste the URL.\n3  Set Width / Height to the values above. Enable Control audio via OBS.\n4  For video, start the camera and add the Camera Source URL.",12,muted);guide.setLineSpacing(dp(2),1f);root.addView(guide,full(112));
        ui.postDelayed(statusTick,900);
    }

    private EditText numberField(String value){EditText e=new EditText(this);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setText(value);e.setTextSize(18);e.setTextColor(white);e.setGravity(Gravity.CENTER);e.setSelectAllOnFocus(true);e.setBackground(rounded(panel2,0xff34425e,12));return e;}
    private void setSize(int w,int h){widthField.setText(String.valueOf(w));heightField.setText(String.valueOf(h));}
    private int field(EditText e,int fallback,int min,int max){try{return Math.max(min,Math.min(max,Integer.parseInt(e.getText().toString())));}catch(Exception x){return fallback;}}
    private String hostUrl(){return "http://"+localIp()+":"+(server==null?0:server.getPort());}
    private String sourceUrl(String source){int w=field(widthField,1280,320,3840),h=field(heightField,720,240,2160);return hostUrl()+"/?source="+source+"&w="+w+"&h="+h;}
    private String localIp(){try{ArrayList<InetAddress> other=new ArrayList<>();for(Enumeration<NetworkInterface> n=NetworkInterface.getNetworkInterfaces();n.hasMoreElements();){NetworkInterface ni=n.nextElement();if(!ni.isUp()||ni.isLoopback())continue;for(Enumeration<InetAddress>a=ni.getInetAddresses();a.hasMoreElements();){InetAddress x=a.nextElement();if(x instanceof Inet4Address&&!x.isLoopbackAddress()&&x.isSiteLocalAddress()){if(ni.getName().startsWith("wlan"))return x.getHostAddress();other.add(x);}}}if(!other.isEmpty())return other.get(0).getHostAddress();}catch(Exception ignored){}return "Wi-Fi unavailable";}
    private void copySource(String source){if(!live){Toast.makeText(this,"Start the microphone first",Toast.LENGTH_SHORT).show();return;}String url=sourceUrl(source);((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Luna OBS "+source+" source",url));Toast.makeText(this,(source.equals("camera")?"Camera":"Audio")+" URL copied · set OBS size to "+field(widthField,1280,320,3840)+" × "+field(heightField,720,240,2160),Toast.LENGTH_LONG).show();}

    private void requestMic(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);else startStream();}
    private void requestCamera(){if(!live){Toast.makeText(this,"Start the mic stream first",Toast.LENGTH_SHORT).show();return;}if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},2);else startCamera();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED){if(r==1)startStream();else if(r==2)startCamera();}else Toast.makeText(this,r==1?"Microphone permission is needed to stream audio":"Camera permission is needed to stream video",Toast.LENGTH_LONG).show();}

    private void startStream(){
        if(live)return;
        try{
            if("Wi-Fi unavailable".equals(localIp()))throw new IllegalStateException("Connect this phone to Wi-Fi before starting the OBS stream");
            int min=AudioRecord.getMinBufferSize(48000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IllegalStateException("This phone does not support 48 kHz microphone capture");
            int buffer=Math.max(min,480*2*4);
            recorder=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION).setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(buffer).build();
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Microphone could not be initialized");
            server=new StreamServer(this);server.start();recorder.startRecording();live=true;
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            audioThread=new Thread(()->{short[] samples=new short[480];while(live){int n=recorder.read(samples,0,samples.length,AudioRecord.READ_BLOCKING);if(n>0){byte[] pcm=new byte[n*2];for(int i=0;i<n;i++){int x=Math.max(-32768,Math.min(32767,samples[i]*gain/100));pcm[2*i]=(byte)x;pcm[2*i+1]=(byte)(x>>>8);}StreamServer s=server;if(s!=null)s.publishPcm(pcm);}else if(n<0){if(live){live=false;ui.post(()->{stopStream();if(!isFinishing())Toast.makeText(this,"Microphone stream stopped. Tap Start Mic to reconnect.",Toast.LENGTH_LONG).show();});}break;}}},"luna-mic");audioThread.start();
            try{WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);wifiLock=wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"LunaPhoneMicCam");wifiLock.acquire();}catch(Exception ignored){}
            address.setText(hostUrl());status.setText("●  WAITING FOR OBS");status.setTextColor(cyan);micButton.setText("■");micButton.setTextColor(0xffff6680);micCaption.setText("STOP MIC");micCaption.setTextColor(0xffff8391);streamLed.setTextColor(cyan);
        }catch(Exception e){stopStream();new AlertDialog.Builder(this).setTitle("Could not start audio").setMessage(e.getMessage()==null?"Check microphone permission and try again.":e.getMessage()).setPositiveButton("OK",null).show();}
    }

    private final Runnable statusTick=new Runnable(){@Override public void run(){if(status==null)return;if(live&&server!=null){int n=server.getAudioClientCount();status.setText(n>0?"●  OBS CONNECTED":"●  WAITING FOR OBS");status.setTextColor(n>0?green:cyan);if(streamLed!=null)streamLed.setTextColor(n>0?green:cyan);if(clientState!=null)clientState.setText(n>0?"OBS AUDIO LINK CONNECTED  ·  LOCAL NETWORK ONLY":"OPEN THE AUDIO SOURCE IN OBS  ·  LOCAL NETWORK ONLY");}ui.postDelayed(this,900);}};
    private void stopStream(){live=false;getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);stopCamera();try{if(server!=null)server.stop();}catch(Exception ignored){}server=null;try{if(recorder!=null){recorder.stop();recorder.release();}}catch(Exception ignored){}recorder=null;try{if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();}catch(Exception ignored){}wifiLock=null;if(address!=null)address.setText("Start the mic to create your OBS link");if(status!=null){status.setText("●  OFFLINE");status.setTextColor(muted);}if(clientState!=null)clientState.setText("PRIVATE TO YOUR LOCAL NETWORK  ·  NO ADS  ·  NO CLOUD");if(micButton!=null){micButton.setText("◖");micButton.setTextColor(cyan);}if(micCaption!=null){micCaption.setText("START MIC");micCaption.setTextColor(muted);}if(streamLed!=null)streamLed.setTextColor(0xff64718a);}

    private void startCamera(){
        if(!live||cameraOn)return;
        final int generation=++cameraGeneration;
        try{
            CameraManager manager=(CameraManager)getSystemService(Context.CAMERA_SERVICE);String selected=null;
            for(String id:manager.getCameraIdList()){Integer facing=manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);if(facing!=null&&facing==(frontCamera?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK)){selected=id;break;}}
            if(selected==null)throw new IllegalStateException("Selected camera is not available");
            CameraCharacteristics chars=manager.getCameraCharacteristics(selected);StreamConfigurationMap map=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);Size[] sizes=map.getOutputSizes(ImageFormat.YUV_420_888);if(sizes==null||sizes.length==0)throw new IllegalStateException("Camera does not support live video output");
            Size chosen=null;long bestArea=0;for(Size z:sizes){long area=(long)z.getWidth()*z.getHeight();if(z.getWidth()<=1280&&z.getHeight()<=720&&area>bestArea){chosen=z;bestArea=area;}}if(chosen==null){chosen=sizes[0];for(Size z:sizes)if((long)z.getWidth()*z.getHeight()<(long)chosen.getWidth()*chosen.getHeight())chosen=z;}
            imageReader=ImageReader.newInstance(chosen.getWidth(),chosen.getHeight(),ImageFormat.YUV_420_888,3);
            cameraThread=new HandlerThread("luna-camera");cameraThread.start();cameraHandler=new Handler(cameraThread.getLooper());imageReader.setOnImageAvailableListener(r->{Image image=null;try{image=r.acquireLatestImage();if(image!=null){byte[] nv21=toNv21(image);ByteArrayOutputStream jpeg=new ByteArrayOutputStream();new YuvImage(nv21,ImageFormat.NV21,image.getWidth(),image.getHeight(),null).compressToJpeg(new Rect(0,0,image.getWidth(),image.getHeight()),78,jpeg);StreamServer s=server;if(s!=null)s.publishJpeg(jpeg.toByteArray());}}catch(Exception ignored){}finally{if(image!=null)image.close();}},cameraHandler);
            final String cameraId=selected;manager.openCamera(cameraId,new CameraDevice.StateCallback(){public void onOpened(CameraDevice c){if(generation!=cameraGeneration||!live){c.close();return;}camera=c;try{c.createCaptureSession(Collections.singletonList(imageReader.getSurface()),new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession s){if(generation!=cameraGeneration||!live){s.close();return;}captureSession=s;try{CaptureRequest.Builder request=c.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);request.addTarget(imageReader.getSurface());request.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);Range<Integer> fps=bestFps(chars);if(fps!=null)request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,fps);s.setRepeatingRequest(request.build(),null,cameraHandler);cameraOn=true;if(server!=null)server.setCameraActive(true);runOnUiThread(()->{cameraButton.setText("CAMERA LIVE");cameraButton.setTextColor(green);});}catch(Exception e){runOnUiThread(()->cameraFailure(e));}}public void onConfigureFailed(CameraCaptureSession s){runOnUiThread(()->cameraFailure(new Exception("This camera cannot start the selected output mode")));}},cameraHandler);}catch(Exception e){runOnUiThread(()->cameraFailure(e));}}public void onDisconnected(CameraDevice c){c.close();runOnUiThread(()->{if(generation==cameraGeneration&&camera==c)stopCamera();});}public void onError(CameraDevice c,int e){c.close();runOnUiThread(()->{if(generation==cameraGeneration)cameraFailure(new Exception("Camera error code "+e));});}},cameraHandler);
        }catch(Exception e){cameraFailure(e);}
    }
    private Range<Integer> bestFps(CameraCharacteristics chars){Range<Integer>[] ranges=chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);if(ranges==null)return null;Range<Integer> best=null;for(Range<Integer> r:ranges){if(r.getUpper()>=24&&r.getUpper()<=30&&(best==null||r.getUpper()>best.getUpper()))best=r;}return best;}
    private byte[] toNv21(Image image){int w=image.getWidth(),h=image.getHeight();byte[] out=new byte[w*h*3/2];Image.Plane[] p=image.getPlanes();ByteBuffer y=p[0].getBuffer(),u=p[1].getBuffer(),v=p[2].getBuffer();int pos=0;for(int row=0;row<h;row++)for(int col=0;col<w;col++)out[pos++]=y.get(row*p[0].getRowStride()+col*p[0].getPixelStride());for(int row=0;row<h/2;row++)for(int col=0;col<w/2;col++){out[pos++]=v.get(row*p[2].getRowStride()+col*p[2].getPixelStride());out[pos++]=u.get(row*p[1].getRowStride()+col*p[1].getPixelStride());}return out;}
    private void cameraFailure(Exception e){stopCamera();new AlertDialog.Builder(this).setTitle("Camera unavailable").setMessage(e.getMessage()==null?"Try switching cameras and start again.":e.getMessage()).setPositiveButton("OK",null).show();}
    private void stopCamera(){cameraGeneration++;cameraOn=false;if(server!=null)server.setCameraActive(false);try{if(captureSession!=null)captureSession.close();}catch(Exception ignored){}captureSession=null;try{if(camera!=null)camera.close();}catch(Exception ignored){}camera=null;try{if(imageReader!=null)imageReader.close();}catch(Exception ignored){}imageReader=null;if(cameraThread!=null){cameraThread.quitSafely();cameraThread=null;cameraHandler=null;}if(cameraButton!=null){cameraButton.setText("ENABLE CAMERA");cameraButton.setTextColor(white);}}

    @Override protected void onDestroy(){ui.removeCallbacks(statusTick);stopStream();super.onDestroy();}
}


