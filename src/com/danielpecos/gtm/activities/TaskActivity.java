package com.danielpecos.gtm.activities;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;

import com.danielpecos.gtm.R;
import com.danielpecos.gtm.model.TaskManager;
import com.danielpecos.gtm.model.beans.Context;
import com.danielpecos.gtm.model.beans.Project;
import com.danielpecos.gtm.model.beans.Task;
import com.danielpecos.gtm.receivers.AlarmReceiver;
import com.danielpecos.gtm.utils.ActivityUtils;
import com.danielpecos.gtm.views.TaskViewHolder;
import com.google.android.maps.GeoPoint;

public class TaskActivity extends TabActivity {
	public static final String FULL_RELOAD = "full_reload";
	public static final String FILE_NAME = "file_name";

	private TaskManager taskManager;
	private Context context;
	private Project project;
	
	public static Task task;
	private static Task originalTask;

	public static TaskViewHolder taskInfoViewHolder;
	public static TaskViewHolder taskReminderViewHolder;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Long context_id = (Long)getIntent().getSerializableExtra("context_id");
		Long project_id = (Long)getIntent().getSerializableExtra("project_id");
		Long task_id = (Long)getIntent().getSerializableExtra("task_id");

		taskManager = TaskManager.getInstance(this);
		context = taskManager.getContext(context_id);
		if (project_id != null) {
			project = context.getProject(project_id);
			task = project.getTask(task_id);
		} else {
			project = null;
			task = context.getTask(task_id);
		}

		originalTask = (Task)task.clone();

		this.initializeUI();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu_task_activity, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.menu_save: {
			closeSavingChanges();
			return true;
		}
		/*case R.id.menu_delete: {
			closeAndDiscardChanges();
			return true;
		}*/
		case R.id.menu_revert: {
			closeAndDiscardChanges();
			return true;
		}
		}
		return false;
	}

	private void initializeUI() {
		setContentView(R.layout.activity_layout_task);

		this.setTitle(task.getName());
		
		Resources res = getResources(); 
		TabHost tabHost = getTabHost(); 
		TabHost.TabSpec spec;  

		Intent intent = new Intent().setClass(this, TaskTabInfoActivity.class);
		spec = tabHost.newTabSpec("details").setIndicator(getString(R.string.task_tab_details),
				res.getDrawable(android.R.drawable.ic_menu_info_details))
				.setContent(intent);
		tabHost.addTab(spec);

		intent = new Intent().setClass(this, TaskTabReminderActivity.class);
		spec = tabHost.newTabSpec("reminder").setIndicator(getString(R.string.task_tab_reminder),
				res.getDrawable(android.R.drawable.ic_popup_reminder))
				.setContent(intent);
		tabHost.addTab(spec);

		tabHost.setOnTabChangedListener(new OnTabChangeListener() {
			@Override
			public void onTabChanged(String tabId) {
				if (tabId.equalsIgnoreCase("details")) {
					taskInfoViewHolder.updateView(TaskActivity.this);
				} else if (tabId.equalsIgnoreCase("reminder")) {
					taskReminderViewHolder.updateView(TaskActivity.this);
				}
			}
		});

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case ActivityUtils.MAP_ACTIVITY:
			if (resultCode == Activity.RESULT_OK) {
				GeoPoint point = new GeoPoint(data.getIntExtra(TaskMapActivity.LATITUD, 0), data.getIntExtra(TaskMapActivity.LONGITUD, 0));
				task.setLocation(point);
				taskReminderViewHolder.updateView(this);
			}
		}
	}
	
	@Override
	public void onBackPressed() {
		//Handle the back button
		this.close();
	}

	private void close() {
		if (task.hashCode() != originalTask.hashCode()) {
			Log.d(TaskManager.TAG, "TaskActivity: task was modified");
			new AlertDialog.Builder(this)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(R.string.task_quit_title)
			.setMessage(R.string.task_quit_message)
			.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					closeSavingChanges();
				}
			})
			.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					closeAndDiscardChanges();
				}
			})
			.show();
		} else {
			Log.d(TaskManager.TAG, "TaskActivity: task wasn't modified");
			TaskActivity.this.setResult(RESULT_CANCELED);
			this.finish();
		}
	}

	private void closeSavingChanges() {
		Log.d(TaskManager.TAG, "TaskActivity: close activity saving changes");
		task.store(TaskActivity.this);
		this.setAlarm();
		Intent resultIntent = new Intent();
		if (task.getName().equalsIgnoreCase(originalTask.getName()) 
				&& (task.getDescription() == null || task.getDescription().equalsIgnoreCase(originalTask.getDescription())) 
				&& task.getPriority().equals(originalTask.getPriority())) {

			Log.d(TaskManager.TAG, "TaskActivity: taskViewHolder refresh required");
			resultIntent.putExtra(TaskActivity.FULL_RELOAD, false);
		} else {
			Log.d(TaskManager.TAG, "TaskActivity: full reload required");
			resultIntent.putExtra(TaskActivity.FULL_RELOAD, true);
		}
		this.setResult(RESULT_OK, resultIntent);
		this.finish();  
	}

	private void setAlarm() {
		if (task.getDueDate() != null) {
			Intent intent = new Intent(this, AlarmReceiver.class);
			intent.putExtra("task_id", task.getId());
			intent.putExtra("project_id", project != null ? project.getId() : null);
			intent.putExtra("context_id", context.getId());

			PendingIntent appIntent = PendingIntent.getBroadcast(this, 0, intent, 0);

			AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
			am.set(AlarmManager.RTC_WAKEUP, task.getDueDate().getTime(), appIntent);
		}
	}

	private void closeAndDiscardChanges() {
		Log.d(TaskManager.TAG, "TaskActivity: close activity discarding changes");
		task.reload(TaskActivity.this);
		TaskActivity.this.setResult(RESULT_CANCELED);
		TaskActivity.this.finish();   
	}

}
