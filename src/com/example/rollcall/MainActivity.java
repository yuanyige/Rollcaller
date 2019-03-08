package com.example.rollcall;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {

	// 界面相关
	Spinner spChooseClass;
	ListView list;
	TextView textInfo;		// 显示信息用的文本框
	Button btnStart;
	Button btnSave;
	Button btnAdd;
	View.OnClickListener btnStartListener;
	View.OnClickListener btnSaveListener;
	View.OnClickListener btnAddListener;
	
	//AlertDialog deleteDialog;
	
	ArrayAdapter<String> adapterForInClass;// = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);		// 开始点名前显示学生信息的适配器
	ArrayAdapter<String> adapterForRollCall;// =new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);		// 点名时和点名结束后显示结果的适配器
	ArrayAdapter<String> adapterClasses;	// 选择班级的spinner的适配器
	
	// 文件存储相关
	File sdCard;		//Environment.getExternalStorageDirectory();
	File pathInfo; //= new File(sdCard,"/RollCall/Info");
	File pathRecord; //= new File(sdCard,"/RollCall/Record");
	String calssFileName;					// 班级文件名
	BufferedReader reader;
	BufferedWriter writer;
	
	// 保存结果相关
	String classNameOfFileName;
	String result;
	boolean hasResult = false;
	
	// 保存从文件读取到的学生信息
	ArrayList<Student> allStudentsInClass = new ArrayList<>();		// 班上所有学生
	ArrayList<Student> attendantStudents = new ArrayList<>();		// 保存扫描到的学生
	
	// 标志变量
	boolean rollCalling = false;
	boolean doneOnceRollCall = false;			// 标记完成一次点名，在点击“结束点名”按钮后置为true
	boolean unregistered = true;					// 标记广播接收器是否已经注销
	boolean timerCanceled = true;				// 标记定时器是否取消
	
	// WLAN相关
	WifiManager wifi;
	BroadcastReceiver wifiResultReceiver;
	Timer wifiScanTimer;
	
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        spChooseClass = (Spinner) findViewById(R.id.spinnerChooseClass);
        list = (ListView) findViewById(R.id.listViewStudents);
        textInfo = (TextView) findViewById(R.id.textInfo);
        btnStart = (Button) findViewById(R.id.btnStart);
        btnSave = (Button) findViewById(R.id.btnSave);
        btnAdd = (Button) findViewById(R.id.btnAddStudent);
        
        textInfo.setTextColor(getResources().getColor(android.R.color.holo_red_light));
        
       // textInfo.setTypeface(getResources().BOLD); 
        
        adapterForRollCall = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);		// 点名时和点名结束后显示结果的适配器
        
        wifiScanTimer = new Timer(true);
        
        //btnSave.setClickable(false);		// 一开始保存记录按钮是不能按的
        //btnSave.setAlpha(0.5f);
        
        sdCard = Environment.getExternalStorageDirectory();
        pathInfo = new File(sdCard,"/RollCall/Info");
    	pathRecord = new File(sdCard,"/RollCall/Record");
        if(!pathInfo.exists())		// 没有Info文件夹则创建文件夹
        {
        	pathInfo.mkdirs();
        }
        if(!pathRecord.exists())	// 没有Record文件夹则创建文件夹
        {
        	pathRecord.mkdirs();
        }
        
        
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);		// 获得wifi管理器
        wifiResultReceiver = new BroadcastReceiver() {						// 扫描到一个新的WLAN节点的监听
			@Override
			public void onReceive(Context context, Intent intent) {
				List<ScanResult> scanResult = wifi.getScanResults();
				addToAttendantStudents(scanResult,allStudentsInClass,attendantStudents,adapterForRollCall);		// 把新的扫描结果根据规则添加到到堂学生集合中,并更新适配器数据
				refreshDisplay();							// 刷新显示，在其中更新数据适配器，并刷新信息显示文本框
			}
		};
        
		// 添加学生的记录
		btnAddListener = new OnClickListener() {
			@Override
			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				LayoutInflater inflater = MainActivity.this.getLayoutInflater();
				
				final View dialogView = inflater.inflate(R.layout.dialog_add_student, null);
				
				((EditText) dialogView.findViewById(R.id.className)).setText((String) spChooseClass.getSelectedItem());		// 默认是当前选择的班级
				builder.setView(dialogView)
				.setPositiveButton("保存", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String className = ((EditText) dialogView.findViewById(R.id.className)).getText().toString();
						String sID = ((EditText) dialogView.findViewById(R.id.id)).getText().toString();
						String name = ((EditText) dialogView.findViewById(R.id.name)).getText().toString();
						String MAC = ((EditText) dialogView.findViewById(R.id.MAC)).getText().toString();
						if((className==null||sID==null||name==null||MAC==null)||
								(className.length()==0
								||sID.length()==0
								||name.length()==0
								||MAC.length()==0))
						{
							Toast.makeText(MainActivity.this,"请填充信息", Toast.LENGTH_SHORT).show();
							return;
						}
					
						boolean isNum = true;
						for(int i=0;i<sID.length();i++)
						{
							if((sID.charAt(i)-'0')<0||(sID.charAt(i)-'0')>9)
							{
								isNum = false;
								break;
							}
						}
						if(!isNum)
						{
							Toast.makeText(MainActivity.this,"学号必须为数字", Toast.LENGTH_SHORT).show();
							return;
						}
						
						addStudent(className+".txt",Integer.parseInt(sID),name,MAC);
					}
				})
				.setNegativeButton("取消", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				})
				.show();
			}
		};
		btnAdd.setOnClickListener(btnAddListener);
        
        // 开始点名按钮的响应
        btnStartListener = new OnClickListener() {
			@Override
			public void onClick(View v) {
				if(rollCalling)		// 正在点名
				{
					rollCalling = false;
					wifiScanTimer.purge();
					timerCanceled = true;
					unregisterReceiver(wifiResultReceiver);		// 注销扫描到wlan接入点的监听
					unregistered = true;
					//btnSave.setClickable(true);		// 一开始保存记录按钮是不能按的
			        //btnSave.setAlpha(1.0f);
					hasResult = true;
					
					result = getResult(allStudentsInClass,attendantStudents);	// 统计点名结果，用于显示在文本框控件上
					textInfo.setText(result);
					btnStart.setText("开始点名");
					
					//spChooseClass.setClickable(true);			// 可以选择班级了
					spChooseClass.setEnabled(true);			// 可以选择班级了
					btnAdd.setClickable(true);
				}
				else				// 还没开始点名
				{
					if(calssFileName!=null)
					{
						if(wifi.isWifiEnabled())
						{
							rollCalling = true;
							registerReceiver(wifiResultReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));		// 注册有扫描结果的广播监听
							
							//　周期开始wifi扫描
							wifiScanTimer.schedule(new TimerTask() {
								@Override
								public void run() {
									wifi.startScan();
								}
							}, 500, 10000);
							timerCanceled = false;
							hasResult = false;
							
							
							classNameOfFileName = new String(calssFileName);
							unregistered = false;
							
							//spChooseClass.setClickable(false);			// 禁用选择班级了
							spChooseClass.setEnabled(false);			// 禁用选择班级了
							btnAdd.setClickable(false);
							
							
							attendantStudents.clear();					// 清空上一次点名的结果
							adapterForRollCall.clear();
							list.setAdapter(adapterForRollCall);		// 切换数据适配器
							
							textInfo.setText("正在点名...");
							btnStart.setText("停止点名");
						}
						else
						{
							Toast.makeText(MainActivity.this, "请先开启wifi", Toast.LENGTH_SHORT).show();
						}
					}
					else
					{
						Toast.makeText(MainActivity.this, "班级为空，无法点名！", Toast.LENGTH_SHORT).show();
					}
				}
			}
		};
        btnStart.setOnClickListener(btnStartListener);
        
        // 保存记录按钮的响应
        btnSaveListener = new OnClickListener() {
			@Override
			public void onClick(View v) {
				if(hasResult)
				{
					saveResult();
					Toast.makeText(MainActivity.this,"结果已保存",Toast.LENGTH_SHORT).show();
					hasResult = false;
				}
				else
				{
					Toast.makeText(MainActivity.this, "还没有点名结果，请先点名", Toast.LENGTH_SHORT).show();
				}
					//btnSave.setClickable(false);		// 一开始保存记录按钮是不能按的
			        //btnSave.setAlpha(0.5f);
			}
		};
        btnSave.setOnClickListener(btnSaveListener);
        
       // 列表控件ListView的某个选项被长按时的响应
        list.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				if(!rollCalling)		// 正在点名则没有响应
				{
					String temp = (String) parent.getItemAtPosition(position);		// 获取点击行的文本数据
					String[] a = temp.split("\t");
					final int studentId = Integer.parseInt(a[0]);			// 获取学号

					AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);	// 对话框构造器
					builder.setTitle("提示");
					builder.setMessage("确认删除该记录？");
					builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							deleteStudentFromAdapterAndArraylistForIncalss(pathInfo,calssFileName+".txt",allStudentsInClass,adapterForInClass,studentId);	// 删除记录
						}
					});
					builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.cancel();
						}
					});
					builder.show();
					
				}
				return true;
				//return false;
			}
        	
		});
        
        
        // 选择班级的响应
        spChooseClass.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,int position, long id) {
				calssFileName = adapterClasses.getItem(position);		// 获得选择的班级
				adapterForInClass = makeAdapterAndArrayListForInclass(pathInfo,calssFileName+".txt",allStudentsInClass);
				list.setAdapter(adapterForInClass);	
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				// TODO Auto-generated method stub
			}
		});
    }
    
    @Override
    protected void onStart()
    {
    	super.onStart();

    	updateClassSpinner();
    }
    
    @Override
    protected void onPause()
    {
    	super.onPause();
    	if(!unregistered && !rollCalling)	// 没在点名又没注销广播监听，则应该关闭
    	{
    		unregisterReceiver(wifiResultReceiver);
    		
    	}
    	if(!timerCanceled&&!rollCalling)
    	{
    		wifiScanTimer.cancel();
    	}
    }
    
    @Override
    protected void onStop()
    {
    	super.onStop();
    }
    
    
    /**
	 * 该方法从文件中读取学生信息，保存在传入的ArrayList<Student>中，并生成数据适配器作为返回值返回
	 */
    private ArrayAdapter<String> makeAdapterAndArrayListForInclass(File path,String calssFileName,ArrayList<Student> allStudentsInClass)
    {
    	File toRead = new File(path,calssFileName);
    	try {
			reader = new BufferedReader(new FileReader(toRead));
		
			allStudentsInClass.clear();
			String line;
			while(true)	//line=reader.readLine())!=null
			{
				line=reader.readLine();
				if(line==null)
					break;
				String[] info = line.split(" ");
				int id = Integer.parseInt(info[0]);		// 学号
				String name = info[1];
				String MAC = info[2];
				allStudentsInClass.add(new Student(id,name,MAC));
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
    	
    	Comparator<Student> comparator = new Comparator<Student>() {

			@Override
			public int compare(Student arg0, Student arg1) {

				if(arg0.id>arg1.id)
					return 1;
				if(arg0.id==arg1.id)
					return 0;
				else 
					return -1;
			}
    	};
     Collections.sort(allStudentsInClass, comparator);
    	
    	
    	
    	ArrayAdapter<String> adp = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
    	for(int i=0;i<allStudentsInClass.size();i++)
    	{
    		String s = ""+allStudentsInClass.get(i).id+"\t"+allStudentsInClass.get(i).name;
    		adp.add(s);
    	}
    	return adp;
    }
    
    /**
     * 该方法接收两个参数，一个是应该出席的学生的集合，一个是已经出席的学生的集合，返回字符串表达统计结果
     * @param studentsShouldAttendant 应该出席的学生的集合
     * @param studentsAttendant 已经出席的学生的集合
     * @return 表达统计结果的字符串
     */
    private String getResult(ArrayList<Student> studentsShouldAttendant,ArrayList<Student> studentsAttendant)
    {
    	String result = "未到学生:\n";
    	
    	for(int i=0;i<studentsShouldAttendant.size();i++)
    	{
    		boolean found = false;
    		for(int j=0;j<studentsAttendant.size();j++)
    		{
    			if(studentsShouldAttendant.get(i).id==studentsAttendant.get(j).id)
    			{
    				found = true;
    				break;
    			}
    		}
    		if(!found)
    		{
    			result += studentsShouldAttendant.get(i).name+"\n";
    		}
    	}
    	
    	return result;
    }
    
    /**
     * 该方法根据wifi扫描结果，把属于该班的学生（即扫描结果中MAC地址属于该班级）添加到到堂学生集合中，并更新用于listview显示的数据适配器以刷新显示
     * @param scanResult wifi扫描结果
     * @param allStudentsInClass 该班级的所有学生的集合
     * @param attendantStudents 到堂的学生的集合
     * @param adapterForRollCall 正在点名时使用的适配器
     */
    private void addToAttendantStudents(List<ScanResult> scanResults,ArrayList<Student> allStudentsInClass,ArrayList<Student> attendantStudents,ArrayAdapter<String> adapterForRollCall)
    {
    	for(ScanResult ap:scanResults)
    	{
    		boolean alreadyExist = false;
    		for(Student st:attendantStudents)
    		{
    			if(st.MAC.toUpperCase().equals(ap.BSSID.toUpperCase()))
    			{
    				alreadyExist = true;
    				break;
    			}
    		}
    		if(alreadyExist)
    			break;
    		
    		for(int i=0;i<allStudentsInClass.size();i++)
    		{
    			if(allStudentsInClass.get(i).MAC.toUpperCase().equals(ap.BSSID.toUpperCase()))
    			{
    				attendantStudents.add(allStudentsInClass.get(i));
    				adapterForRollCall.add(allStudentsInClass.get(i).id+"\t"+allStudentsInClass.get(i).name+"\t	√");
    			}
    		}
    	}
    }
    
    /**
     * 该方法刷新显示，包括：1、刷新listview显示；2、刷新文本框
     */
    private void refreshDisplay()
    {
    	adapterForRollCall.notifyDataSetChanged();		// 通知数据集改变
    	textInfo.setText("应到人数： "+allStudentsInClass.size()+"\n"+"已到人数："+attendantStudents.size());
    }
    
    /**
     * 给定学生的学号，从数据适配器和文件里删除对应学生的记录
     * @param path 文件路径
     * @param calssFileName 文件名
     * @param allStudentsInClass 班上所有学生集合
     * @param adapterForInClass 数据适配器
     * @param studentId 学号
     */
    private void deleteStudentFromAdapterAndArraylistForIncalss(
    		File path,
    		String calssFileName,
    		ArrayList<Student> allStudentsInClass,
    		ArrayAdapter<String> adapterForInClass,
    		int studentId)
    {
    	// 从数据集中删除学生
    	int i=0;
    	boolean found = false;
    	for(i=0;i<allStudentsInClass.size();i++)
    	{
    		if(allStudentsInClass.get(i).id==studentId)
    		{
    			found = true;
    			break;
    		}
    	}
    	if(found)
    		allStudentsInClass.remove(i);
    	
    	// 清空数据适配器并重新添加学生
    	adapterForInClass.clear();
    	for(i=0;i<allStudentsInClass.size();i++)
    	{
    		String s = ""+allStudentsInClass.get(i).id+"\t"+allStudentsInClass.get(i).name;
    		adapterForInClass.add(s);
    	}
    	adapterForInClass.notifyDataSetChanged();
    	
    	// 从文件里删除学生
    	File file = new File(path,calssFileName);
    	file.delete();
    	file = new File(path,calssFileName);
    	try {
			writer = new BufferedWriter(new FileWriter(file));
			for(Student st:allStudentsInClass)
			{
				writer.write(st.id+" "+st.name+" "+st.MAC+"\n");
			}
			writer.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
    	
    }
    
    private void addStudent(String className,int id,String studentName,String MAC)
    {
    	
    	// pathInfo-学生信息文件的保存目录
    	
    	if(className.equals(calssFileName+".txt"))
    	{ 
    		// 如果增加的是当前选中班级的学生，那么对应需要把该学生添加到学生集合里
        	Student studentToAdd = new Student(id, studentName, MAC);
        	allStudentsInClass.add(studentToAdd);
        	
        	// 相应的数据适配器也要改变
        	adapterForInClass.add(studentToAdd.id+"\t"+studentToAdd.name+"\t");
        	adapterForInClass.notifyDataSetChanged();
    	}
    	
    	
    	// 在文件中增加学生记录
    	File file = new File(pathInfo,className);
    	try {
			writer = new BufferedWriter(new FileWriter(file,true));
			writer.append(""+id+" "+studentName+" "+MAC+"\n");
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	
    	if(!className.equals(calssFileName+".txt"))
    	{
    		updateClassSpinner();
    	}
    }
    
    	
    	
    	/*未实现的去重功能
    	
    	// pathInfo-学生信息文件的保存目录
    	// 如果增加的是当前选中班级的学生，那么对应需要把该学生添加到学生集合里
    	if(className.equals(calssFileName+".txt"))
    	{ 
    		
        	boolean exist = false;
    		for(int i=0;i<allStudentsInClass.size();i++)
        	{
        		if(allStudentsInClass.get(i).id==id)
        		{
        			allStudentsInClass.get(i).name = studentName;
        			allStudentsInClass.get(i).MAC = MAC;
        			exist = true;
        			break;
        		}
        		
        	}
    		if(!exist)
    		{
    			Student studentToAdd = new Student(id, studentName, MAC);
            	allStudentsInClass.add(studentToAdd);
    		}
    		
        	
        	
        	//相应的数据适配器也要改变
        	adapterForInClass.add(studentToAdd.id+"\t"+studentToAdd.name+"\t");
    		adapterForInClass.clear();
    		adapterForInClass.addAll(allStudentsInClass);
        	adapterForInClass.notifyDataSetChanged();
    	}
    	
    	
    	// 在文件中增加学生记录
    	File file = new File(pathInfo,className);
    	try {
			writer = new BufferedWriter(new FileWriter(file,true));
			writer.append(""+id+" "+studentName+" "+MAC+"\n");
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	
    	if(!className.equals(calssFileName+".txt"))
    	{
    		updateClassSpinner();
    	}
    	*/
    
    
    private void saveResult()
    {
    	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm");
    	String s = sdf.format(new Date());
    	File saveFile = new File(pathRecord, s+"_"+classNameOfFileName+".txt");
    	try {
			writer = new BufferedWriter(new FileWriter(saveFile));
			writer.write(result);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    /**
     * 更新班级下拉列表的方法
     */
    private void updateClassSpinner()
    {
    	String[] classes = pathInfo.list();		// 获取所有文件的文件名
    	if(classes!=null&&classes.length>0)
    	{
    		for(int i=0;i<classes.length;i++)
        	{
    			if(classes[i].length() >4)
        		classes[i]=classes[i].substring(0,classes[i].length()-4);
        	}
    		adapterClasses = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, classes);
    		spChooseClass.setAdapter(adapterClasses);		// 将班级文件名作为选项
    		
    		
    		int size = spChooseClass.getCount();
    		for(int i=0;i<size;i++)
    		{
    			if(((String)spChooseClass.getItemAtPosition(i)).equals(calssFileName))
    			{
    				spChooseClass.setSelection(i);
    				break;
    			}
    		}
    		
    	}
    }
    
}
