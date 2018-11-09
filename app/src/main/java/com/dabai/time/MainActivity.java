package com.dabai.time;

import android.app.*;
import android.os.*;
import android.view.*;
import java.io.*;
import android.*;
import android.widget.*;
import android.content.*;
import java.text.*;
import java.util.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.support.design.widget.*;
import android.net.*;

public class MainActivity extends Activity 
{
	String form = null;
	String color_b,color_t;
	int text_size;


	TextView ontext;

	private EditText ed1;

	private EditText ed2;

	private EditText ed3;

	private EditText ed4;





    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
	
		init();
		onWindowFocusChanged(true);

		ontext = (TextView)findViewById(R.id.mainTextView1);

		handler.post(task);

		
		
			ontext.setBackgroundColor(Color.parseColor(color_b));
			ontext.setTextColor(Color.parseColor(color_t));
			ontext.setTextSize(text_size);
			
	
			ontext.setOnLongClickListener(new View.OnLongClickListener() {  

	            @Override  
	            public boolean onLongClick(View v)
				{  

					showDia();

	                return true;  
	            }  
	        });  
			
    }

	
	
	
	
	//设置界面
	public void showDia()
	{
		
		LayoutInflater inflater = LayoutInflater.from(this);
		View diaview = inflater.inflate(R.layout.setting, null);

		 ed1 = (EditText)diaview.findViewById(R.id.ed1);
		 ed2 = (EditText)diaview.findViewById(R.id.ed2);
		 ed3 = (EditText)diaview.findViewById(R.id.ed3);
		 ed4 = (EditText)diaview.findViewById(R.id.ed4);
		 
		 
		 
		
		ed1.setText(form);
		ed2.setText(color_b);
		ed3.setText(color_t);
		ed4.setText(""+text_size);
		
		
		
		new AlertDialog.Builder(this)
			.setView(diaview)
			.setCancelable(false)
			.setPositiveButton("确认更改",
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialogInterface, int i)
				{
					//储存
				
					SharedPreferences.Editor editor = getSharedPreferences("date", MODE_WORLD_WRITEABLE).edit(); 
					editor.putString("format",ed1.getText().toString());
					editor.putString("color_b",ed2.getText().toString());
					editor.putString("color_t", ed3.getText().toString());
					editor.putInt("text_size",Integer.parseInt(ed4.getText().toString()));
					//editor.putBoolean("tip",sw1.isChecked());
					editor.commit();
				
					
					Intent intent = new Intent(getApplicationContext(), MainActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					getApplicationContext().startActivity(intent);
					android.os.Process.killProcess(android.os.Process.myPid());
				}
				
			}) 
			.setNeutralButton("取消", null)
			.show();
	}

//弹窗方法(提示)
	public void showTip(String txt,int mode){
		if(mode==1){
			Toast.makeText(getApplicationContext(),txt,1).show();
		}

		if(mode==2){
			Snackbar.make(getWindow().getDecorView(),txt,Snackbar.LENGTH_INDEFINITE).show();
		}

		if(mode==3){
			new AlertDialog.Builder(this).setTitle("提示").setMessage(txt).show();
		}

	}

	interface rain{
		public static int TOAST=1;
		public static int SNACK=2;
		public static int ALERT=3;
	}

//线程
	private Handler handler = new Handler();   
    private Runnable task = new Runnable() {  
        public void run()
		{   
			handler.postDelayed(this, 1000);//设置循环时间，此处是5秒
			//取得当前时间
			SimpleDateFormat sdf = new SimpleDateFormat(form);
			
			SimpleDateFormat sdf1 = new SimpleDateFormat("mmss");
			if(sdf1.format(new Date()).equals("0000")){
			
				sendMess("整点报时",sdf.format(new Date()));
			}	
			ontext.setText(sdf.format(new Date()));

        }   
    };


//发送通知
	public void sendMess(String title,String txt){
		Notification notification = new Notification.Builder(this)
			.setContentTitle(title)//设置标题
			.setContentText(txt)
			.setWhen(System.currentTimeMillis())//设置创建时间
			.setSmallIcon(R.drawable.ic_launcher)//设置状态栏图标
			.setDefaults(Notification.DEFAULT_LIGHTS)
			.setSound(Uri.parse("android.resource://com.dabai.time/" +R.raw.elegant))
			.setPriority(Notification.PRIORITY_MAX)
			.setColor(Color.RED)
			//.setLargeIcon(BitmapFactory.decodeResource(getResources(),R.drawable.ic_launcher))//设置通知栏图标
			.build();

		NotificationManager manager = (NotificationManager) this.getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
		manager.notify(1, notification);
	
	}
	
	
	public void init()
	{
		//不要忘了在清单申请
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
		{
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1); // 动态申请读取权
		}

		File file = new File("/sdcard/.time.txt");
		if (!file.exists())
		{
			showTip("默认配置生效\n长按主界面打开设置",3);
			saveSdFile(".time.txt", "true");
			//储存
			SharedPreferences.Editor editor = getSharedPreferences("date", MODE_WORLD_WRITEABLE).edit(); 
			editor.putString("format", "HH:mm:ss");
			editor.putString("color_b", "#000000");
			editor.putString("color_t", "#ffffff");
			editor.putInt("text_size", 60);
			editor.putBoolean("tip",false);

			editor.commit();

			form = "HH:mm:ss";
			color_b = "#000000";
			color_t = "#ffffff";
			text_size = 60;
			
		}
		else
		{
			//获取
			SharedPreferences read = getSharedPreferences("date", MODE_WORLD_READABLE); 
		
			form = read.getString("format", "");
			color_b = read.getString("color_b", "");
			color_t = read.getString("color_t", "");
			text_size = read.getInt("text_size", 0);
	

			//showTip(read.getBoolean("tip",false)+"",1);
			//Toast.makeText(getApplicationContext(),"默认配置生效"+color_t+color_b+text_size,1).show();

		}



	}
	//filename   a.txt  不加路径保存到sdcard
	public void saveSdFile(String filename, String text)
	{
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
		{	
			try
			{
				File sdCardDir = Environment.getExternalStorageDirectory();//获取SDCard目录
				File saveFile = new File(sdCardDir, filename);
				FileOutputStream outStream = new FileOutputStream(saveFile);
				outStream.write(text.getBytes());
				outStream.close();
			}
			catch (IOException ioe)
			{
			}
		}
	}

	@Override
    public void onWindowFocusChanged(boolean hasFocus)
	{
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && Build.VERSION.SDK_INT >= 19) 
		{
			getWindow().getDecorView().setSystemUiVisibility
			(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
				View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
				View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
			);
        }
    }
	
	

}
