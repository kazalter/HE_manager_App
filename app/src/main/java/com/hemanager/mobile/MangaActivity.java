package com.hemanager.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import android.view.ViewGroup;

public class MangaActivity extends Activity {
    private static final int COLOR_BG = Color.BLACK;
    private static final int MODE_PAGE = 0;
    private static final int MODE_SCROLL = 1;
    private static final String PREF_READING_MODE = "viewer_reading_mode";

    private String serverUrl;
    private String token;
    private int id;
    private String mediaType;
    private int[] playlistIds;
    private int page = 0;
    private int totalPages = 1;
    private int loadGeneration = 0;
    private int lastSavedServerProgress = -1;
    private int readingMode = MODE_PAGE;
    private boolean restartFromBeginning = false;
    private boolean progressReady = false;
    private boolean restoringScroll = false;
    private static final int MANGA_PREFETCH_AHEAD = 6;
    private static final int MANGA_PREFETCH_BEHIND = 2;
    private static final int IMAGE_PREFETCH_AHEAD = 2;
    private static final int IMAGE_PREFETCH_BEHIND = 1;
    private static final int IMAGE_PREFETCH_MAX_IN_FLIGHT = 3;
    private static final float SCROLL_HIGH_QUALITY_ZOOM = 2.15f;
    private static final int SCROLL_MAX_DECODE_WIDTH = 3200;
    /**
     * 连续滚动模式下"常规质量"的解码宽度倍率。
     *
     * 原来是 2.0——每页都按屏宽两倍解码，1080p 手机上一张长条漫画页就是
     * 2160×3000 的 RGB_565 位图（约 13MB），再叠加前后 8 页预取，几乎必然
     * 把 LruCache 打穿并反复重解码。实际显示宽度只有屏宽，1.25 倍已经足够
     * 覆盖轻微缩放；真正放大到 {@link #SCROLL_HIGH_QUALITY_ZOOM} 以上时，
     * {@link #refreshVisibleContinuousPagesForZoom} 会把可见页按 3 倍重解一次。
     */
    private static final float SCROLL_BASE_DECODE_MULTIPLIER = 1.25f;
    /** 本地图片缓存目录的容量上限，超出后按最后访问时间淘汰。 */
    private static final long IMAGE_DISK_CACHE_BUDGET_BYTES = 512L * 1024L * 1024L;
    /** 进度上报防抖窗口：连续翻页时只在停下来之后发一次 PATCH。 */
    private static final long PROGRESS_SAVE_DEBOUNCE_MS = 900L;
    /** 页高缓存单独放一个 prefs 文件，不再污染 he_manager 主配置。 */
    private static final String PREFS_PAGE_SIZES = "he_manager_page_sizes";

    private final Set<Integer> prefetchInFlight = new HashSet<>();
    private final Set<Future<?>> prefetchTasks = new HashSet<>();
    private final Map<Integer, Object> pageDownloadLocks = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> bitmapDecodeWidths = new ConcurrentHashMap<>();
    private LruCache<Integer, Bitmap> bitmapCache;

    /**
     * 图片下载 / 解码线程池。
     *
     * 之前翻页模式用的是 {@code AsyncTask.execute()}，即 **全局串行** 执行器：
     * 所有页面的下载排成一条队，还和进度上报、总页数请求共用同一条队列。
     * 一页卡在 30 秒读超时，后面每一页和每一次进度保存都得等它。
     * 换成独立线程池后各页并行，且与网络上报互不阻塞。
     */
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "manga-io");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    /** 进度上报单独一条线，保证不会被图片下载堵住。 */
    private final ExecutorService netExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "manga-net");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressSaveTask = () -> postProgressToServer(currentProgress());

    private FrameLayout root;
    private ViewPager2 pager;
    private WebtoonZoomLayout zoomLayout;
    private RecyclerView continuousRecycler;
    private ContinuousAdapter continuousAdapter;
    private TextView pageIndicator;
    private TextView settingsButton;
    private LinearLayout modePanel;
    private TextView pageModeButton;
    private TextView scrollModeButton;
    private final Runnable hideControls = () -> {
        if (pageIndicator == null) return;
        pageIndicator.animate()
                .alpha(0f)
                .setDuration(220)
                .withEndAction(() -> {
                    if (pageIndicator != null) pageIndicator.setVisibility(View.GONE);
                })
                .start();
        if (settingsButton != null) {
            settingsButton.animate()
                    .alpha(0f)
                    .setDuration(220)
                    .withEndAction(() -> {
                        if (settingsButton != null) settingsButton.setVisibility(View.GONE);
                    })
                    .start();
        }
        if (modePanel != null) {
            modePanel.animate()
                    .alpha(0f)
                    .setDuration(160)
                    .withEndAction(() -> {
                        if (modePanel != null) modePanel.setVisibility(View.GONE);
                    })
                    .start();
        }
    };

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);

        int cacheSize = (int) Math.max(8L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 4);
        bitmapCache = new LruCache<Integer, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(Integer key, Bitmap value) {
                return value == null ? 0 : value.getByteCount();
            }

            @Override
            protected void entryRemoved(boolean evicted, Integer key, Bitmap oldValue, Bitmap newValue) {
                if (key != null && newValue == null) bitmapDecodeWidths.remove(key);
            }
        };

        serverUrl = ApiClient.trimSlash(getIntent().getStringExtra("server_url"));
        token = getIntent().getStringExtra("token");
        id = getIntent().getIntExtra("id", 0);
        mediaType = getIntent().getStringExtra("media_type");
        playlistIds = getIntent().getIntArrayExtra("playlist_ids");
        setTitle(getIntent().getStringExtra("title"));
        readingMode = getSharedPreferences("he_manager", MODE_PRIVATE).getInt(PREF_READING_MODE, MODE_PAGE);
        page = Math.max(0, getIntent().getIntExtra("progress", 0));
        lastSavedServerProgress = page;

        restartFromBeginning = getIntent().getBooleanExtra("restart", false);
        if (restartFromBeginning) {
            page = 0;
            lastSavedServerProgress = -1;
            getSharedPreferences("he_manager", MODE_PRIVATE)
                .edit()
                .remove("progress_" + id)
                .apply();
        }

        pager = new ViewPager2(this);
        pager.setBackgroundColor(COLOR_BG);
        pager.setAdapter(new MangaPagerAdapter());
        pager.setOffscreenPageLimit(2);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (page != position) {
                    page = position;
                    saveProgressToServer(false);
                    if (pageIndicator != null && pageIndicator.getVisibility() == View.VISIBLE) {
                        pageIndicator.setText((page + 1) + " / " + totalPages);
                    }
                }
                prefetchPagesAround(position);
            }
        });

        continuousRecycler = new RecyclerView(this);
        continuousRecycler.setBackgroundColor(COLOR_BG);
        LinearLayoutManager continuousLayout = new LinearLayoutManager(this);
        continuousLayout.setInitialPrefetchItemCount(4);
        continuousRecycler.setLayoutManager(continuousLayout);
        continuousRecycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        continuousRecycler.setItemAnimator(null);
        continuousRecycler.setItemViewCacheSize(8);
        continuousRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (Math.abs(dy) > 2) dismissControls();
                updatePageFromScroll();
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm != null) {
                    int last = lm.findLastVisibleItemPosition();
                    if (last != RecyclerView.NO_POSITION) {
                        prefetchPagesAround(last);
                    }
                }
            }
        });

        zoomLayout = new WebtoonZoomLayout(this);
        zoomLayout.setBackgroundColor(COLOR_BG);
        zoomLayout.setRecyclerView(continuousRecycler);
        zoomLayout.setOnSingleTapListener(() -> {
            updatePageFromScroll();
            showControls();
        });
        zoomLayout.setOnZoomSettledListener(scale -> {
            if (readingMode == MODE_SCROLL && scale >= SCROLL_HIGH_QUALITY_ZOOM) {
                refreshVisibleContinuousPagesForZoom();
            }
        });
        zoomLayout.addView(continuousRecycler, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        zoomLayout.setVisibility(View.GONE);

        root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        root.addView(pager, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(zoomLayout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        pageIndicator = new TextView(this);
        pageIndicator.setTextColor(Color.WHITE);
        pageIndicator.setTextSize(15f);
        pageIndicator.setGravity(Gravity.CENTER);
        pageIndicator.setPadding(dp(14), dp(7), dp(14), dp(7));
        pageIndicator.setAlpha(0f);
        pageIndicator.setVisibility(View.GONE);

        GradientDrawable indicatorBackground = new GradientDrawable();
        indicatorBackground.setColor(0xCC111318);
        indicatorBackground.setCornerRadius(dp(18));
        pageIndicator.setBackground(indicatorBackground);

        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        indicatorParams.topMargin = dp(28);
        root.addView(pageIndicator, indicatorParams);

        settingsButton = new TextView(this);
        settingsButton.setText("设置");
        settingsButton.setTextColor(Color.WHITE);
        settingsButton.setTextSize(13f);
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setPadding(dp(12), dp(7), dp(12), dp(7));
        settingsButton.setAlpha(0f);
        settingsButton.setVisibility(View.GONE);
        GradientDrawable settingsBackground = new GradientDrawable();
        settingsBackground.setColor(0xCC111318);
        settingsBackground.setCornerRadius(dp(18));
        settingsButton.setBackground(settingsBackground);
        settingsButton.setOnClickListener(view -> toggleModePanel());
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        settingsParams.topMargin = dp(28);
        settingsParams.rightMargin = dp(18);
        root.addView(settingsButton, settingsParams);

        modePanel = new LinearLayout(this);
        modePanel.setOrientation(LinearLayout.VERTICAL);
        modePanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        modePanel.setAlpha(0f);
        modePanel.setVisibility(View.GONE);
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(0xF012151C);
        panelBackground.setCornerRadius(dp(8));
        panelBackground.setStroke(dp(1), 0x33FFFFFF);
        modePanel.setBackground(panelBackground);

        pageModeButton = modeButton("翻页阅读", MODE_PAGE);
        scrollModeButton = modeButton("纵向连续", MODE_SCROLL);
        modePanel.addView(pageModeButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollButtonParams.topMargin = dp(6);
        modePanel.addView(scrollModeButton, scrollButtonParams);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                dp(136),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        panelParams.topMargin = dp(70);
        panelParams.rightMargin = dp(18);
        root.addView(modePanel, panelParams);

        setContentView(root);
        trimImageDiskCache();
        loadPageCount();
    }

    private void loadPageCount() {
        if (!"manga".equals(mediaType)) {
            totalPages = 1;
            if (playlistIds != null && playlistIds.length > 0) {
                totalPages = playlistIds.length;
                for (int i = 0; i < playlistIds.length; i++) {
                    if (playlistIds[i] == id) {
                        page = i;
                        break;
                    }
                }
            }
            if (pager != null && pager.getAdapter() != null) {
                pager.getAdapter().notifyDataSetChanged();
            }
            renderReadingMode();
            return;
        }
        final int fallbackPage = page;
        ioExecutor.execute(() -> {
            final JSONObject result = new JSONObject();
            try {
                ApiClient client = new ApiClient(serverUrl, token);
                if (!restartFromBeginning) {
                    JSONObject media = client.getJsonObject("/mobile/media/" + id);
                    result.put("progress", Math.max(0, media.optInt("progress", fallbackPage)));
                }
                JSONObject pages = client.getJsonObject("/mobile/manga/" + id + "/pages");
                result.put("total_pages", Math.max(1, pages.optInt("total_pages", 1)));
            } catch (Exception e) {
                try {
                    result.put("total_pages", 1);
                    result.put("progress", fallbackPage);
                } catch (Exception ignored) {
                }
            }
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                totalPages = Math.max(1, result.optInt("total_pages", 1));
                if (!restartFromBeginning) {
                    page = Math.max(0, result.optInt("progress", page));
                    lastSavedServerProgress = page;
                }
                if (page >= totalPages) {
                    page = Math.max(0, totalPages - 1);
                }
                if (page < 0) page = 0;
                progressReady = true;
                if (pager != null && pager.getAdapter() != null) {
                    pager.getAdapter().notifyDataSetChanged();
                }
                renderReadingMode();
                if (restartFromBeginning) {
                    saveProgressToServer(true);
                }
            });
        });
    }

    private String pageUrl(int pageIndex, boolean trackProgress) {
        String path;
        if ("manga".equals(mediaType)) {
            path = "/mobile/manga/" + id + "/page/" + pageIndex;
        } else {
            int targetId = (playlistIds != null && playlistIds.length > 0 && pageIndex >= 0 && pageIndex < playlistIds.length) 
                    ? playlistIds[pageIndex] : id;
            path = "/mobile/stream/" + targetId;
        }
        String url = serverUrl + path + "?" + ApiClient.tokenQuery(token);
        if ("manga".equals(mediaType) && trackProgress) {
            url += "&track_progress=true";
        }
        return url;
    }

    private File cachedImageFile(String url, int pageIndex) throws Exception {
        File dir = new File(getCacheDir(), "viewer-images");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Cannot create image cache");
        }
        String key;
        if ("manga".equals(mediaType)) {
            key = "media_" + id + "_page_" + pageIndex + ".img";
        } else {
            int targetId = (playlistIds != null && playlistIds.length > 0 && pageIndex >= 0 && pageIndex < playlistIds.length) 
                    ? playlistIds[pageIndex] : id;
            key = "media_" + targetId + "_image.img";
        }
        File file = new File(dir, key);
        if (file.exists() && file.length() > 0) return file;

        Object lock = pageDownloadLocks.computeIfAbsent(pageIndex, k -> new Object());
        synchronized (lock) {
            if (file.exists() && file.length() > 0) return file;

            File temp = new File(dir, key + "." + System.nanoTime() + ".tmp");
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Accept", "image/*,*/*");
            try {
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new RuntimeException("HTTP " + code);
                }
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[32 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            } catch (Exception e) {
                if (temp.exists()) temp.delete();
                throw e;
            } finally {
                conn.disconnect();
            }

            if (file.exists() && file.length() > 0) {
                temp.delete();
                return file;
            }
            if (!temp.renameTo(file)) {
                if (file.exists() && file.length() > 0) {
                    temp.delete();
                    return file;
                }
                temp.delete();
                throw new RuntimeException("Cannot save image cache");
            }
            return file;
        }
    }

    private void renderReadingMode() {
        dismissControls();
        updateModeButtons();
        if (readingMode == MODE_SCROLL) {
            pager.setVisibility(View.GONE);
            zoomLayout.setVisibility(View.VISIBLE);
            loadContinuousPages();
        } else {
            zoomLayout.setVisibility(View.GONE);
            pager.setVisibility(View.VISIBLE);
            pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
            pager.setCurrentItem(page, false);
        }
        prefetchPagesAround(page);
    }

    private void setReadingMode(int mode) {
        if (readingMode == mode) {
            dismissControls();
            return;
        }
        if (mode == MODE_PAGE) {
            updatePageFromScroll();
        }
        readingMode = mode;
        getSharedPreferences("he_manager", MODE_PRIVATE)
                .edit()
                .putInt(PREF_READING_MODE, readingMode)
                .apply();
        renderReadingMode();
    }

    private void loadContinuousPages() {
        loadGeneration++;
        zoomLayout.resetZoom(false);
        continuousAdapter = new ContinuousAdapter();
        continuousRecycler.setAdapter(continuousAdapter);
        final int targetPage = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
        restoringScroll = true;
        continuousRecycler.post(() -> {
            if (readingMode != MODE_SCROLL) return;
            LinearLayoutManager lm = (LinearLayoutManager) continuousRecycler.getLayoutManager();
            if (lm != null) lm.scrollToPositionWithOffset(targetPage, 0);
            continuousRecycler.postDelayed(() -> restoringScroll = false, 700);
        });
    }

    private void updatePageFromScroll() {
        if (continuousRecycler == null || readingMode != MODE_SCROLL) return;
        if (restoringScroll) return;
        LinearLayoutManager lm = (LinearLayoutManager) continuousRecycler.getLayoutManager();
        if (lm == null) return;
        int first = lm.findFirstVisibleItemPosition();
        int last = lm.findLastVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) return;

        int viewportCenter = continuousRecycler.getHeight() / 2;
        int bestPage = first;
        int minDist = Integer.MAX_VALUE;
        for (int i = first; i <= last; i++) {
            View child = lm.findViewByPosition(i);
            if (child == null) continue;
            int childCenter = (child.getTop() + child.getBottom()) / 2;
            int dist = Math.abs(childCenter - viewportCenter);
            if (dist < minDist) {
                minDist = dist;
                bestPage = i;
            }
        }
        int nextPage = Math.max(0, Math.min(bestPage, totalPages - 1));
        if (nextPage != page) {
            page = nextPage;
            saveProgressToServer(false);
        }
    }

    private void refreshVisibleContinuousPagesForZoom() {
        if (continuousRecycler == null || continuousAdapter == null || readingMode != MODE_SCROLL) return;
        LinearLayoutManager lm = (LinearLayoutManager) continuousRecycler.getLayoutManager();
        if (lm == null) return;
        int first = lm.findFirstVisibleItemPosition();
        int last = lm.findLastVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return;
        first = Math.max(0, first - 1);
        last = Math.min(totalPages - 1, last + 1);
        int viewportWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        int desiredWidth = targetScrollDecodeWidth(viewportWidth, zoomLayout == null ? 1f : zoomLayout.getCurrentScale());
        for (int i = first; i <= last; i++) {
            Integer cachedWidth = bitmapDecodeWidths.get(i);
            if (cachedWidth == null || cachedWidth < desiredWidth * 0.90f) {
                continuousAdapter.notifyItemRangeChanged(first, last - first + 1, "zoom-quality");
                return;
            }
        }
    }

    private TextView modeButton(String label, int mode) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(14f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(9), dp(10), dp(9));
        button.setOnClickListener(view -> setReadingMode(mode));
        return button;
    }

    private void updateModeButtons() {
        styleModeButton(pageModeButton, readingMode == MODE_PAGE);
        styleModeButton(scrollModeButton, readingMode == MODE_SCROLL);
    }

    private void styleModeButton(TextView button, boolean selected) {
        if (button == null) return;
        button.setTextColor(selected ? Color.BLACK : Color.WHITE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? 0xFF8EA7FF : 0x22111318);
        background.setCornerRadius(dp(8));
        background.setStroke(dp(1), selected ? 0x668EA7FF : 0x22FFFFFF);
        button.setBackground(background);
    }

    private void showControls() {
        if (pageIndicator == null) return;
        if (readingMode == MODE_SCROLL) {
            updatePageFromScroll();
        }
        
        pageIndicator.removeCallbacks(hideControls);
        pageIndicator.animate().cancel();
        pageIndicator.setText((page + 1) + " / " + totalPages);
        updateModeButtons();

        if (pageIndicator.getVisibility() != View.VISIBLE) {
            pageIndicator.setAlpha(0f);
            pageIndicator.setVisibility(View.VISIBLE);
        }
        pageIndicator.animate()
                .alpha(1f)
                .setDuration(160)
                .withEndAction(() -> pageIndicator.postDelayed(hideControls, 2500))
                .start();

        if (settingsButton != null) {
            settingsButton.animate().cancel();
            if (settingsButton.getVisibility() != View.VISIBLE) {
                settingsButton.setAlpha(0f);
                settingsButton.setVisibility(View.VISIBLE);
            }
            settingsButton.animate().alpha(1f).setDuration(160).start();
        }
    }

    private void toggleModePanel() {
        if (modePanel == null) return;
        pageIndicator.removeCallbacks(hideControls);
        updateModeButtons();
        if (modePanel.getVisibility() == View.VISIBLE) {
            modePanel.animate()
                    .alpha(0f)
                    .setDuration(140)
                    .withEndAction(() -> modePanel.setVisibility(View.GONE))
                    .start();
            pageIndicator.postDelayed(hideControls, 1200);
            return;
        }
        modePanel.animate().cancel();
        modePanel.setAlpha(0f);
        modePanel.setVisibility(View.VISIBLE);
        modePanel.animate()
                .alpha(1f)
                .setDuration(150)
                .withEndAction(() -> pageIndicator.postDelayed(hideControls, 2500))
                .start();
    }

    private void dismissControls() {
        if (pageIndicator == null) return;
        pageIndicator.removeCallbacks(hideControls);
        pageIndicator.animate().cancel();
        pageIndicator.setAlpha(0f);
        pageIndicator.setVisibility(View.GONE);
        if (settingsButton != null) {
            settingsButton.animate().cancel();
            settingsButton.setAlpha(0f);
            settingsButton.setVisibility(View.GONE);
        }
        if (modePanel != null) {
            modePanel.animate().cancel();
            modePanel.setAlpha(0f);
            modePanel.setVisibility(View.GONE);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 防抖中的那一次要立刻落盘，别等窗口过期。
        mainHandler.removeCallbacks(progressSaveTask);
        saveProgressToServer(true);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(progressSaveTask);
        if (pageIndicator != null) {
            pageIndicator.removeCallbacks(hideControls);
            pageIndicator.animate().cancel();
        }
        if (settingsButton != null) settingsButton.animate().cancel();
        if (modePanel != null) modePanel.animate().cancel();
        synchronized (prefetchTasks) {
            for (Future<?> task : prefetchTasks) {
                task.cancel(true);
            }
            prefetchTasks.clear();
        }
        synchronized (prefetchInFlight) {
            prefetchInFlight.clear();
        }
        ioExecutor.shutdownNow();
        netExecutor.shutdown();
        if (bitmapCache != null) bitmapCache.evictAll();
        super.onDestroy();
    }

    /**
     * 裁剪本地图片缓存。
     *
     * {@code viewer-images} 之前只写不删：看过的每一页都永久留在设备上，一个
     * 大漫画库能轻松堆到几个 GB。启动时在后台按最后修改时间淘汰到
     * {@link #IMAGE_DISK_CACHE_BUDGET_BYTES} 以内。
     */
    private void trimImageDiskCache() {
        ioExecutor.execute(() -> {
            try {
                File dir = new File(getCacheDir(), "viewer-images");
                File[] files = dir.listFiles();
                if (files == null || files.length == 0) return;
                long total = 0L;
                List<File> candidates = new ArrayList<>(Arrays.asList(files));
                for (File file : candidates) total += file.length();
                if (total <= IMAGE_DISK_CACHE_BUDGET_BYTES) return;
                Comparator<File> oldestFirst = (a, b) -> Long.compare(a.lastModified(), b.lastModified());
                candidates.sort(oldestFirst);
                for (File file : candidates) {
                    if (total <= IMAGE_DISK_CACHE_BUDGET_BYTES) break;
                    long size = file.length();
                    if (file.delete()) total -= size;
                }
            } catch (Exception ignored) {
            }
        });
    }

    private int currentProgress() {
        return Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
    }

    /**
     * 上报阅读进度。
     *
     * 非强制调用走 {@link #PROGRESS_SAVE_DEBOUNCE_MS} 防抖：连续模式下
     * {@code onScrolled} 每次跨页都会调一次，快速翻过两百页的漫画原本会打出
     * 两百个串行 PATCH 请求（还排在图片下载同一条队列里）。现在只在停下来
     * 之后发一次；onPause / onDestroy 用 force=true 立即落盘，不丢进度。
     */
    private void saveProgressToServer(boolean force) {
        if (!progressReady) return;
        final int progress = currentProgress();
        if (!force && progress == lastSavedServerProgress) return;
        mainHandler.removeCallbacks(progressSaveTask);
        if (force) {
            postProgressToServer(progress);
        } else {
            mainHandler.postDelayed(progressSaveTask, PROGRESS_SAVE_DEBOUNCE_MS);
        }
    }

    private void postProgressToServer(final int progress) {
        if (!progressReady) return;
        lastSavedServerProgress = progress;
        netExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("progress", progress);
                new ApiClient(serverUrl, token).patchJson("/media/" + id, body, true);
            } catch (Exception ignored) {
            }
        });
    }

    private android.content.SharedPreferences pageSizePrefs() {
        // 页高缓存以前和 server_url / token 挤在同一个 he_manager 文件里，
        // 每页一个 key、从不清理——看过几千页之后，App 启动时要同步解析一个
        // 几千条目的 XML 才能读到服务器地址。拆到独立文件后互不影响。
        return getSharedPreferences(PREFS_PAGE_SIZES, MODE_PRIVATE);
    }

    private String pageHeightKey(int pageIndex) {
        if ("manga".equals(mediaType)) {
            return "page_height_" + id + "_" + pageIndex;
        } else {
            int targetId = (playlistIds != null && playlistIds.length > 0 && pageIndex >= 0 && pageIndex < playlistIds.length) 
                    ? playlistIds[pageIndex] : id;
            return "page_height_image_" + targetId;
        }
    }

    private void prefetchPagesAround(int center) {
        if (totalPages <= 1) return;
        int ahead = "manga".equals(mediaType) ? MANGA_PREFETCH_AHEAD : IMAGE_PREFETCH_AHEAD;
        int behind = "manga".equals(mediaType) ? MANGA_PREFETCH_BEHIND : IMAGE_PREFETCH_BEHIND;
        int from = Math.max(0, center - behind);
        int to = Math.min(totalPages - 1, center + ahead);
        for (int p = center; p <= to; p++) prefetchPage(p);
        for (int p = center - 1; p >= from; p--) prefetchPage(p);
    }

    private void prefetchPage(final int pageIndex) {
        if (pageIndex < 0 || pageIndex >= totalPages) return;
        if (bitmapCache != null && bitmapCache.get(pageIndex) != null) return;
        synchronized (prefetchInFlight) {
            if (prefetchInFlight.contains(pageIndex)) return;
            if (!"manga".equals(mediaType) && prefetchInFlight.size() >= IMAGE_PREFETCH_MAX_IN_FLIGHT) return;
            prefetchInFlight.add(pageIndex);
        }
        final String url = pageUrl(pageIndex, false);
        final int viewportWidth = readerViewportWidth();
        // 预取一律按"常规质量"解码。放大到高清阈值以上时只重解可见的那几页
        // （refreshVisibleContinuousPagesForZoom），不要让 8 页预取都吃 3 倍内存。
        final int decodeWidth = targetScrollDecodeWidth(viewportWidth, 1f);
        final Future<?>[] holder = new Future<?>[1];
        Future<?> task = ioExecutor.submit(() -> {
            try {
                File file = cachedImageFile(url, pageIndex);
                if (Thread.currentThread().isInterrupted()) return;
                if (readingMode == MODE_SCROLL && bitmapCache != null
                        && bitmapCache.get(pageIndex) == null) {
                    Bitmap bm = decodeBitmapForPage(file, decodeWidth);
                    if (bm != null) {
                        bitmapCache.put(pageIndex, bm);
                        bitmapDecodeWidths.put(pageIndex, decodeWidth);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                synchronized (prefetchInFlight) { prefetchInFlight.remove(pageIndex); }
                synchronized (prefetchTasks) { prefetchTasks.remove(holder[0]); }
            }
        });
        holder[0] = task;
        synchronized (prefetchTasks) { prefetchTasks.add(task); }
    }

    /** 阅读区实际宽度（px）。横屏 / 分屏下和屏幕宽度不是一回事。 */
    private int readerViewportWidth() {
        if (continuousRecycler != null && continuousRecycler.getWidth() > 0) {
            return continuousRecycler.getWidth();
        }
        if (pager != null && pager.getWidth() > 0) {
            return pager.getWidth();
        }
        return Math.max(1, getResources().getDisplayMetrics().widthPixels);
    }

    private int targetScrollDecodeWidth(int viewportWidth, float scale) {
        float multiplier = scale >= SCROLL_HIGH_QUALITY_ZOOM
                ? 3.0f
                : SCROLL_BASE_DECODE_MULTIPLIER;
        return Math.max(1, Math.min(SCROLL_MAX_DECODE_WIDTH, Math.round(viewportWidth * multiplier)));
    }

    private Bitmap decodeBitmapForPage(File file, int targetWidth) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth > 0 && bounds.outWidth / sample > targetWidth) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Throwable t) {
            return null;
        }
    }

    private class ContinuousAdapter extends RecyclerView.Adapter<ContinuousAdapter.PageHolder> {
        private final int generation = loadGeneration;

        class PageHolder extends RecyclerView.ViewHolder {
            FrameLayout container;
            ImageView imageView;
            TextView statusText;
            int bindGen = 0;

            PageHolder(FrameLayout v) {
                super(v);
                container = v;
                imageView = new ImageView(MangaActivity.this);
                imageView.setBackgroundColor(COLOR_BG);
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setAdjustViewBounds(true);

                statusText = new TextView(MangaActivity.this);
                statusText.setTextColor(0x99FFFFFF);
                statusText.setTextSize(13f);
                statusText.setGravity(Gravity.CENTER);

                container.addView(imageView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                container.addView(statusText, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
            }
        }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(MangaActivity.this);
            frame.setBackgroundColor(COLOR_BG);
            frame.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
            return new PageHolder(frame);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.bindGen++;
            final int thisGen = holder.bindGen;
            prefetchPagesAround(position);

            final int viewportWidth = readerViewportWidth();
            final int decodeWidth = targetScrollDecodeWidth(viewportWidth, zoomLayout == null ? 1f : zoomLayout.getCurrentScale());
            int cachedHeight = pageSizePrefs().getInt(pageHeightKey(position), 0);
            int height = cachedHeight > 0 ? cachedHeight : viewportWidth;
            ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
            lp.height = height;
            holder.itemView.setLayoutParams(lp);

            Bitmap cached = bitmapCache != null ? bitmapCache.get(position) : null;
            boolean showingCachedBitmap = false;
            if (cached != null && !cached.isRecycled()) {
                holder.statusText.setVisibility(View.GONE);
                holder.imageView.setVisibility(View.VISIBLE);
                holder.imageView.setImageBitmap(cached);
                adjustHeightForBitmap(holder, position, cached);
                showingCachedBitmap = true;
                Integer cachedWidth = bitmapDecodeWidths.get(position);
                if (cachedWidth != null && cachedWidth >= decodeWidth * 0.90f) {
                    return;
                }
            }

            if (!showingCachedBitmap) {
                holder.imageView.setVisibility(View.GONE);
                holder.imageView.setImageBitmap(null);
                holder.statusText.setVisibility(View.VISIBLE);
                holder.statusText.setText((position + 1) + " / " + totalPages);
            }

            final String url = pageUrl(position, false);
            final boolean hadCachedBitmap = showingCachedBitmap;
            ioExecutor.execute(() -> {
                String error = null;
                Bitmap bm = null;
                try {
                    File file = cachedImageFile(url, position);
                    bm = decodeBitmapForPage(file, decodeWidth);
                    if (bm != null && bitmapCache != null) {
                        bitmapCache.put(position, bm);
                        bitmapDecodeWidths.put(position, decodeWidth);
                    }
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final Bitmap result = bm;
                final String errorText = error;
                mainHandler.post(() -> {
                    if (holder.bindGen != thisGen) return;
                    if (generation != loadGeneration) return;
                    if (result == null) {
                        if (!hadCachedBitmap) {
                            holder.statusText.setText(errorText == null ? "加载失败" : errorText);
                        }
                        return;
                    }
                    holder.statusText.setVisibility(View.GONE);
                    holder.imageView.setVisibility(View.VISIBLE);
                    holder.imageView.setImageBitmap(result);
                    adjustHeightForBitmap(holder, position, result);
                });
            });
        }

        private void adjustHeightForBitmap(PageHolder holder, int position, Bitmap bm) {
            int iw = bm.getWidth();
            int ih = bm.getHeight();
            if (iw <= 0 || ih <= 0) return;
            int targetWidth = readerViewportWidth();
            int targetHeight = Math.max(dp(120), Math.round(targetWidth * (ih / (float) iw)));
            ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
            if (lp.height != targetHeight) {
                lp.height = targetHeight;
                holder.itemView.setLayoutParams(lp);
                pageSizePrefs().edit().putInt(pageHeightKey(position), targetHeight).apply();
            }
        }

        @Override
        public int getItemCount() {
            return totalPages;
        }
    }

    private class MangaPagerAdapter extends RecyclerView.Adapter<MangaPagerAdapter.ViewHolder> {
        class ViewHolder extends RecyclerView.ViewHolder {
            FrameLayout container;
            SubsamplingScaleImageView imageView;
            TextView statusText;
            int loadGen = 0;

            ViewHolder(FrameLayout v) {
                super(v);
                container = v;
                imageView = new SubsamplingScaleImageView(MangaActivity.this);
                imageView.setBackgroundColor(COLOR_BG);
                imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE);
                imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER);
                imageView.setDoubleTapZoomScale(2.5f);
                imageView.setDoubleTapZoomDuration(220);
                imageView.setMaxScale(8f);
                imageView.setPanEnabled(true);
                imageView.setZoomEnabled(true);
                imageView.setQuickScaleEnabled(false);
                imageView.setClickable(true);
                // 左右各 28% 是翻页区，中间 44% 呼出控制条——和主流漫画阅读器一致，
                // 单手拿着手机也能翻页，不用每次都划。放大状态下不翻页（此时用户
                // 是在平移画面），交给 SubsamplingScaleImageView 自己处理。
                final GestureDetector tapDetector = new GestureDetector(
                        MangaActivity.this,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapConfirmed(MotionEvent event) {
                                handleReaderTap(imageView, event.getX());
                                return false;
                            }
                        });
                imageView.setOnTouchListener((view, event) -> {
                    tapDetector.onTouchEvent(event);
                    // 返回 false：事件继续交给 SSIV，缩放 / 平移 / 双击放大都不受影响。
                    return false;
                });

                statusText = new TextView(MangaActivity.this);
                statusText.setTextColor(Color.WHITE);
                statusText.setGravity(Gravity.CENTER);

                container.addView(imageView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                container.addView(statusText, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frameLayout = new FrameLayout(MangaActivity.this);
            frameLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            frameLayout.setBackgroundColor(COLOR_BG);
            return new ViewHolder(frameLayout);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.loadGen++;
            final int currentGen = holder.loadGen;
            prefetchPagesAround(position);
            holder.imageView.setVisibility(View.GONE);
            holder.statusText.setVisibility(View.VISIBLE);
            holder.statusText.setText("\u52a0\u8f7d\u4e2d...");

            final String url = pageUrl(position, true);
            // 关键修复：原来这里是 AsyncTask.execute()，走的是**全局串行**执行器。
            // 一页下载卡住，后面所有页面（以及进度上报）全部排队等待，表现为
            // "翻了好几页都停在加载中"。改用本 Activity 自己的线程池并行下载。
            ioExecutor.execute(() -> {
                String error = null;
                File file = null;
                try {
                    file = cachedImageFile(url, position);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final File result = file;
                final String errorText = error;
                mainHandler.post(() -> {
                    if (holder.loadGen != currentGen) return;
                    if (result == null) {
                        holder.statusText.setText(errorText == null ? "加载失败" : errorText);
                        return;
                    }
                    holder.statusText.setVisibility(View.GONE);
                    holder.imageView.setVisibility(View.VISIBLE);
                    holder.imageView.setImage(ImageSource.uri(result.getAbsolutePath()));
                });
            });
        }

        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            // SubsamplingScaleImageView 会把整页切成 tile 缓存在内存里。
            // 回收时不释放的话，offscreenPageLimit=2 意味着 5 页的 tile 常驻，
            // 长条漫画很容易把内存吃满。
            holder.loadGen++;
            holder.imageView.recycle();
            holder.imageView.setVisibility(View.GONE);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return totalPages;
        }
    }

    /**
     * 翻页模式下的点击分区：左 28% 上一页 / 右 28% 下一页 / 中间呼出控制条。
     * 图片已被放大（scale 明显大于 minScale）时一律当作"呼出控制条"，
     * 避免用户在放大浏览细节时误触翻页。
     */
    private void handleReaderTap(SubsamplingScaleImageView imageView, float tapX) {
        if (readingMode != MODE_PAGE || pager == null) {
            showControls();
            return;
        }
        boolean zoomedIn = imageView.getScale() > imageView.getMinScale() * 1.05f;
        int width = imageView.getWidth();
        if (zoomedIn || width <= 0) {
            showControls();
            return;
        }
        if (tapX < width * 0.28f) {
            if (page > 0) {
                pager.setCurrentItem(page - 1, true);
            } else {
                showControls();
            }
        } else if (tapX > width * 0.72f) {
            if (page < totalPages - 1) {
                pager.setCurrentItem(page + 1, true);
            } else {
                showControls();
            }
        } else {
            showControls();
        }
    }
}
