package com.huanmeng.shiyan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.SimpleAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private ListView lvVideos;
    private TextView tvTitle, tvBack, tvEmpty, tvSort;
    private TextView tvTabAll, tvTabFolder;
    
    private List<VideoItem> currentList = new ArrayList<VideoItem>();
    private List<VideoItem> allFolders = new ArrayList<VideoItem>();
    private List<VideoItem> allFiles = new ArrayList<VideoItem>();
    
    private boolean isFolderMode = false; 
    private boolean isInFolder = false;   
    private String currentFolderPath = "";
    
    private static final int REQ_PERMISSION = 100;
    private int currentSortType = 5; 

    private LruCache<String, Bitmap> mMemoryCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            View rootLayout = findViewById(android.R.id.content);
            if (rootLayout != null) {
                rootLayout.setPadding(0, getStatusBarHeight(), 0, 0);
            }
        }

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        lvVideos = (ListView) findViewById(R.id.lvVideos);
        tvTitle = (TextView) findViewById(R.id.tvTitle);
        tvBack = (TextView) findViewById(R.id.tvBack);
        tvEmpty = (TextView) findViewById(R.id.tvEmpty);
        tvSort = (TextView) findViewById(R.id.tvSort);
        tvTabAll = (TextView) findViewById(R.id.tvTabAll);
        tvTabFolder = (TextView) findViewById(R.id.tvTabFolder);

        tvBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (isInFolder) showFolderList(); }
        });
        tvSort.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSortPopup(v); }
        });
        tvTabAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                isFolderMode = false; isInFolder = false;
                updateTabUI(); showAllVideos();
            }
        });
        tvTabFolder.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                isFolderMode = true; isInFolder = false;
                updateTabUI(); showFolderList();
            }
        });

        checkAndRequestPermissions();

        lvVideos.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                VideoItem item = currentList.get(position);
                if (!isFolderMode) {
                    playVideoAt(position);
                } else {
                    if (!isInFolder) showFileList(item.getPath());
                    else playVideoAt(position);
                }
            }
        });
        
        lvVideos.setOnTouchListener(new View.OnTouchListener() {
            float startX = 0;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX(); break;
                    case MotionEvent.ACTION_UP:
                        if (isFolderMode && isInFolder && event.getX() - startX > 150) {
                            showFolderList(); return true; 
                        }
                        break;
                }
                return false; 
            }
        });
        
        isFolderMode = false;
        updateTabUI();
    }
    
    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.READ_MEDIA_VIDEO") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.READ_MEDIA_VIDEO"}, REQ_PERMISSION);
            } else {
                scanVideos();
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQ_PERMISSION);
            } else {
                scanVideos();
            }
        } else {
            scanVideos();
        }
    }
    
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
    
    @Override
    public void onBackPressed() {
        if (isFolderMode && isInFolder) showFolderList();
        else super.onBackPressed();
    }
    
    private void updateTabUI() {
        if (isFolderMode) {
            tvTabAll.setTextColor(Color.parseColor("#888888"));
            tvTabFolder.setTextColor(Color.parseColor("#2185F5"));
        } else {
            tvTabAll.setTextColor(Color.parseColor("#2185F5"));
            tvTabFolder.setTextColor(Color.parseColor("#888888"));
        }
    }
    
    private void playVideoAt(int position) {
        ArrayList<String> videoPaths = new ArrayList<String>();
        for (VideoItem f : currentList) videoPaths.add(f.getPath());
        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
        intent.putStringArrayListExtra("videoList", videoPaths);
        intent.putExtra("videoIndex", position);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] results) {
        if (requestCode == REQ_PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            scanVideos();
        } else {
            // 🌟 权限被拒绝时，弹窗引导用户去设置里手动打开
            new AlertDialog.Builder(this)
                .setTitle("需要存储权限")
                .setMessage("幻梦播放器需要读取视频权限才能正常工作，请前往设置手动开启。")
                .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
            tvEmpty.setText("权限被拒绝，无法扫描视频");
            tvEmpty.setVisibility(View.VISIBLE);
        }
    }

    private void scanVideos() {
        Toast.makeText(MainActivity.this, "开始扫描视频...", Toast.LENGTH_SHORT).show();
        tvEmpty.setText("正在扫描视频...");
        tvEmpty.setVisibility(View.VISIBLE);
        lvVideos.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<VideoItem> tempFolders = new ArrayList<VideoItem>();
                final List<VideoItem> tempFiles = new ArrayList<VideoItem>();
                
                String[] projection = {
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_ADDED
                };
                Cursor cursor = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, null);

                if (cursor != null) {
                    Map<String, List<VideoItem>> folderMap = new LinkedHashMap<String, List<VideoItem>>();
                    while (cursor.moveToNext()) {
                        String path = cursor.getString(0), name = cursor.getString(1);
                        long size = cursor.getLong(2), duration = cursor.getLong(3), dateAdded = cursor.getLong(4);
                        File file = new File(path); String parent = file.getParent();
                        if (parent == null) continue;
                        VideoItem item = new VideoItem(name, path, size, duration, dateAdded);
                        tempFiles.add(item);
                        if (!folderMap.containsKey(parent)) folderMap.put(parent, new ArrayList<VideoItem>());
                        folderMap.get(parent).add(item);
                    }
                    cursor.close();
                    for (Map.Entry<String, List<VideoItem>> entry : folderMap.entrySet()) {
                        File dir = new File(entry.getKey());
                        long totalDuration = 0, latestTime = 0;
                        for (VideoItem v : entry.getValue()) {
                            totalDuration += v.getDuration();
                            if (v.getDateAdded() > latestTime) latestTime = v.getDateAdded();
                        }
                        tempFolders.add(new VideoItem(dir.getName(), entry.getKey(), entry.getValue().size(), totalDuration, latestTime));
                    }
                }
                
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        allFolders.clear();
                        allFolders.addAll(tempFolders);
                        allFiles.clear();
                        allFiles.addAll(tempFiles);
                        
                        if (allFolders.isEmpty() && allFiles.isEmpty()) {
                            tvEmpty.setText("没有找到视频");
                            tvEmpty.setVisibility(View.VISIBLE); 
                            lvVideos.setVisibility(View.GONE);
                        } else {
                            tvEmpty.setVisibility(View.GONE); 
                            lvVideos.setVisibility(View.VISIBLE);
                            applySort(currentSortType);
                            if (isFolderMode) showFolderList(); else showAllVideos();
                            // 🌟 扫描完成提示
                            Toast.makeText(MainActivity.this, "扫描完成，共找到 " + allFiles.size() + " 个视频", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void showSortPopup(View anchorView) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_sort, null);
        final PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setBackgroundDrawable(new BitmapDrawable());
        popupWindow.setOutsideTouchable(true); popupWindow.setFocusable(true);

        popupView.findViewById(R.id.tvSortNameAsc).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { changeSort(0); popupWindow.dismiss(); } });
        popupView.findViewById(R.id.tvSortNameDesc).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { changeSort(1); popupWindow.dismiss(); } });
        popupView.findViewById(R.id.tvSortSizeAsc).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { changeSort(2); popupWindow.dismiss(); } });
        popupView.findViewById(R.id.tvSortSizeDesc).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { changeSort(3); popupWindow.dismiss(); } });
        popupView.findViewById(R.id.tvSortTimeAsc).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { changeSort(4); popupWindow.dismiss(); } });
        popupView.findViewById(R.id.tvSortTimeDesc).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { changeSort(5); popupWindow.dismiss(); } });
        popupWindow.showAsDropDown(anchorView, -100, 10);
    }

    private void changeSort(int sortType) {
        currentSortType = sortType; applySort(currentSortType);
        if (isFolderMode) { if (isInFolder) showFileList(currentFolderPath); else showFolderList(); }
        else showAllVideos();
    }

    private void applySort(int sortType) {
        Comparator<VideoItem> comparator = null;
        switch (sortType) {
            case 0: comparator = new Comparator<VideoItem>() { @Override public int compare(VideoItem o1, VideoItem o2) { return o1.getName().compareToIgnoreCase(o2.getName()); } }; break;
            case 1: comparator = new Comparator<VideoItem>() { @Override public int compare(VideoItem o1, VideoItem o2) { return o2.getName().compareToIgnoreCase(o1.getName()); } }; break;
            case 2: comparator = new Comparator<VideoItem>() { @Override public int compare(VideoItem o1, VideoItem o2) { return Long.compare(o1.getSize(), o2.getSize()); } }; break;
            case 3: comparator = new Comparator<VideoItem>() { @Override public int compare(VideoItem o1, VideoItem o2) { return Long.compare(o2.getSize(), o1.getSize()); } }; break;
            case 4: comparator = new Comparator<VideoItem>() { @Override public int compare(VideoItem o1, VideoItem o2) { return Long.compare(o1.getDateAdded(), o2.getDateAdded()); } }; break;
            case 5: comparator = new Comparator<VideoItem>() { @Override public int compare(VideoItem o1, VideoItem o2) { return Long.compare(o2.getDateAdded(), o1.getDateAdded()); } }; break;
        }
        if (comparator != null) { Collections.sort(allFolders, comparator); Collections.sort(allFiles, comparator); }
    }

    private void showAllVideos() {
        isInFolder = false; tvTitle.setText("所有视频"); tvBack.setVisibility(View.GONE);
        currentList = new ArrayList<VideoItem>(allFiles);
        SimpleAdapter adapter = new SimpleAdapter(this, buildFileData(), R.layout.Item_video, new String[]{"icon", "name", "info", "duration"}, new int[]{R.id.ivThumbnail, R.id.tvName, R.id.tvInfo, R.id.tvDuration});
        setThumbnailBinder(adapter); lvVideos.setAdapter(adapter);
    }

    private void showFolderList() {
        isInFolder = false; tvTitle.setText("视频文件夹"); tvBack.setVisibility(View.GONE);
        currentList = new ArrayList<VideoItem>(allFolders);
        SimpleAdapter adapter = new SimpleAdapter(this, buildFolderData(), R.layout.Item_video, new String[]{"icon", "name", "info", "duration"}, new int[]{R.id.ivThumbnail, R.id.tvName, R.id.tvInfo, R.id.tvDuration});
        setThumbnailBinder(adapter); lvVideos.setAdapter(adapter);
    }

    private void showFileList(String folderPath) {
        isInFolder = true; currentFolderPath = folderPath; tvTitle.setText(new File(folderPath).getName()); tvBack.setVisibility(View.VISIBLE);
        currentList = new ArrayList<VideoItem>();
        for (VideoItem f : allFiles) { if (new File(f.getPath()).getParent().equals(folderPath)) currentList.add(f); }
        SimpleAdapter adapter = new SimpleAdapter(this, buildFileData(), R.layout.Item_video, new String[]{"icon", "name", "info", "duration"}, new int[]{R.id.ivThumbnail, R.id.tvName, R.id.tvInfo, R.id.tvDuration});
        setThumbnailBinder(adapter); lvVideos.setAdapter(adapter);
    }

    private void setThumbnailBinder(SimpleAdapter adapter) {
        adapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object data, String textRepresentation) {
                if (view.getId() == R.id.ivThumbnail && data instanceof String) {
                    String path = (String) data;
                    ImageView imageView = (ImageView) view;
                    if ("folder".equals(path)) {
                        imageView.setImageResource(R.drawable.wenjianjia); 
                    } else {
                        imageView.setTag(path);
                        Bitmap cachedBitmap = getBitmapFromMemCache(path);
                        if (cachedBitmap != null) imageView.setImageBitmap(cachedBitmap);
                        else { imageView.setImageResource(android.R.drawable.ic_media_play); new ThumbnailTask(path, imageView).execute(); }
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private class ThumbnailTask extends AsyncTask<Void, Void, Bitmap> {
        private String path; private ImageView imageView;
        public ThumbnailTask(String path, ImageView imageView) { this.path = path; this.imageView = imageView; }
        @Override protected Bitmap doInBackground(Void... params) {
            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
            if (bitmap != null) addBitmapToMemoryCache(path, bitmap);
            return bitmap;
        }
        @Override protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && imageView != null && imageView.getTag() != null && imageView.getTag().equals(path)) imageView.setImageBitmap(bitmap);
        }
    }

    private void addBitmapToMemoryCache(String key, Bitmap bitmap) { if (getBitmapFromMemCache(key) == null) mMemoryCache.put(key, bitmap); }
    private Bitmap getBitmapFromMemCache(String key) { return mMemoryCache.get(key); }
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());

    private List<Map<String, Object>> buildFolderData() {
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (VideoItem f : allFolders) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("icon", "folder"); map.put("name", f.getName());
            map.put("info", f.getSize() + " 个视频  |  " + sdf.format(new Date(f.getDateAdded() * 1000L)));
            map.put("duration", formatDuration(f.getDuration()));
            data.add(map);
        }
        return data;
    }

    private List<Map<String, Object>> buildFileData() {
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (VideoItem f : currentList) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("icon", f.getPath()); map.put("name", f.getName());
            map.put("info", f.getFormattedSize() + "  |  " + sdf.format(new Date(f.getDateAdded() * 1000L)));
            map.put("duration", f.getFormattedDuration());
            data.add(map);
        }
        return data;
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000, h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        else return String.format("%02d:%02d", m, s);
    }
}