package com.ctt535.start.shambook;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRenderer.Page;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.SimpleBookmark;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PDFContentActivity extends AppCompatActivity {
    static final String PREFS_NAME = "MyPrefsFile";

    private Context context;
    private Handler hLoadPDF;
    private View lastChapter;

    private CustomHorizontalScrollView scrollView;
    private ZoomListView listPdfPages;
    private DrawerLayout tableOfChapters;
    private ListView leftDrawer;
    private WebView webviewContent;

    private ParcelFileDescriptor mFileDescriptor;
    private PdfRenderer mPdfRenderer;
    private Page mCurrentPage;
    private ListPDFViewAdapter lPDFViewAdapter;

    private ImageButton turnBackBtn;
    private ImageButton tableContentBtn;
    private ImageButton settingBtn;

    private BookInformation currentBookInfo;
    private ArrayAdapter settingAdapter;
    private ArrayAdapter chapterAdapter;

    private TextView seekPositionText;
    private Bitmap []listPages;

    private ArrayList<String> listSettingText;
    private ArrayList<Integer> listSettingImage;
    private ArrayList<String> listPdfBooknark;
    private ArrayList<Integer> listBookmarkPosition;

    private int newUiOptions;
    private int numPages;
    private int nextPageIndex = 0;
    private int prePageIndex = 0;
    private int screenWidth;
    private int screenHeight;
    private int lastPage;
    private int currentPage = 0;
    private int seekPagePos = 0;
    private int percentRead = 0;
    private float scalePage = 1.0f;
    private String currentBookPath;
    private String defaultBackgroundColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Hide action bar
        newUiOptions = getWindow().getDecorView().getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 14) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }

        // Status bar hiding: Backwards compatible to Jellybean
        if (Build.VERSION.SDK_INT >= 16) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }

        if (Build.VERSION.SDK_INT >= 18) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getSupportActionBar().hide();
        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);

        setContentView(R.layout.activity_book_content);
        context =this;

        //Get display size
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;

        //Find view by ids and hide webviewContent, because file that is reading is pdf
        webviewContent = (WebView) findViewById(R.id.webviewContent);
        scrollView = (CustomHorizontalScrollView)findViewById(R.id.horizontalScroll);
        listPdfPages = (ZoomListView) findViewById(R.id.listPdfPages);
        webviewContent.setVisibility(View.GONE);

        //Set function for image button in leftview
        setFunctionForLeftImageButton();

        //Get book path store in the previous activity and default background color and percent read
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        currentBookPath = settings.getString("currentBookPath", "");
        defaultBackgroundColor = settings.getString("pdfBackgroundColor", "#ffffff");
        loadPercentRead(0);

        //Find ids of leftview layout
        tableOfChapters = (DrawerLayout) findViewById(R.id.tableOfChapters);
        leftDrawer = (ListView) findViewById(R.id.listChapter);

        //Show pdf content(pages) and bookmark

        try {
            File file = new File(currentBookPath);
            PdfReader pdfReader = new PdfReader(file.getAbsolutePath());
            currentBookInfo = readBookInformation(pdfReader, currentBookPath);
            mFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            mPdfRenderer = new PdfRenderer(mFileDescriptor);
            numPages = mPdfRenderer.getPageCount();
            listPages = new Bitmap[numPages];

            for(int i=0; i< numPages; i++){
                listPages[i] = null;
            }

            settingListPdfPages();
            if(percentRead != 0) {
                currentPage = (int)  Math.round((percentRead * numPages *1.0)/100);
                moveToPage(currentPage, 1);
            }else{
                showFirstBookContent();
            }

            loadPDFBookmark(pdfReader);
        }catch (Exception ex){
            ex.printStackTrace();
        }

        //Initial list setting
        initSettingList();
    }

    @Override
    public void onBackPressed(){
        try {
            mFileDescriptor.close();
            mPdfRenderer.close();
        }catch (Exception e){
            Log.e("mFileDescriptor Error", "Can't close mFileDescriptor");
        }

        ArrayList<BookInformation> lsPath = loadPercentRead(1);
        writeBookRecentRead(lsPath);

        Intent intent = new Intent(PDFContentActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private ArrayList<BookInformation> loadPercentRead(int type){
        //type: 0 - load percent read, 1 - save percenread
        try {
            File secondInputFile = new File(getFilesDir(), "list_recent_book");
            InputStream secondInputStream = new BufferedInputStream(new FileInputStream(secondInputFile));
            BufferedReader r = new BufferedReader(new InputStreamReader(secondInputStream));
            ArrayList<BookInformation> lsPath = new ArrayList<>();

            String line;
            while ((line = r.readLine()) != null) {
                BookInformation bf = new BookInformation();
                bf.filePath = line;
                line = r.readLine();
                bf.precentRead = Integer.parseInt(line);

                if(bf.filePath.equals(currentBookPath)){
                    if(type == 0) {
                        percentRead = bf.precentRead;
                        return null;
                    }else{
                        bf.precentRead = (int)Math.round((currentPage *1.0 *100)/numPages);
                    }
                }
                lsPath.add(bf);
            }
            r.close();
            secondInputStream.close();
            return lsPath;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void writeBookRecentRead(ArrayList<BookInformation> booksOpen){
        String data = "";
        for (int i = 0; i< booksOpen.size() - 1; i++) {
            data += booksOpen.get(i).filePath +"\n";
            data += booksOpen.get(i).precentRead +"\n";
        }
        data += booksOpen.get(booksOpen.size() - 1).filePath + "\n";
        data += booksOpen.get(booksOpen.size() - 1).precentRead;

        try {
            File secondFile = new File(getFilesDir(), "list_recent_book");
            secondFile.createNewFile();
            FileOutputStream fos = new FileOutputStream(secondFile);

            fos.write(data.getBytes());
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setFunctionForLeftImageButton(){
        turnBackBtn = (ImageButton)findViewById(R.id.btn_back_menu);
        tableContentBtn = (ImageButton)findViewById(R.id.btn_table_content);
        settingBtn = (ImageButton) findViewById(R.id.btn_setting);

        turnBackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        tableContentBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTableContent();
            }
        });

        settingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingList();
            }
        });
    }

    private void settingListPdfPages(){
        listPdfPages.setCustomHorizontalScrollView(scrollView);
        listPdfPages.setPinchScaleListner(new ZoomListView.PinchZoomListner() {
            @Override
            public void onPinchZoom(float zoom) {
                ListPDFViewAdapter.ZoomFactor = zoom * scalePage;
                listPdfPages.invalidateViews();
            }

            @Override
            public void onPinchEnd() {
                System.out.println("On Pinch End");
            }

            @Override
            public void onPinchStarted() {
                System.out.println("On Pinch Started");
            }
        });

        listPdfPages.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                currentPage = firstVisibleItem;
                if(nextPageIndex - firstVisibleItem <= 8 && firstVisibleItem - lastPage > 0) {
                    if (numPages - nextPageIndex >= 10) {
                        loadNewPDFPage(10, 0);
                    }else if (numPages - nextPageIndex > 0){
                        loadNewPDFPage(numPages - nextPageIndex, 0);
                    }
                }else if(firstVisibleItem - prePageIndex <= 8  && firstVisibleItem - lastPage < 0){
                    if (prePageIndex >= 10) {
                        loadNewPDFPage(10, 1);
                    }else if(prePageIndex > 0){
                        loadNewPDFPage(prePageIndex, 1);
                    }
                }
                lastPage = firstVisibleItem;
            }
        });
    }

    private BookInformation readBookInformation(PdfReader pdfReader, String currentBookPath){
        BookInformation bookInfo = new BookInformation();

        bookInfo.filePath = currentBookPath;
        bookInfo.title = pdfReader.getInfo().get("Title");
        if(bookInfo.title.isEmpty()){
            String []temp = currentBookPath.split("\\/");
            bookInfo.title = temp[temp.length -1];
        }

        bookInfo.authors = pdfReader.getInfo().get("Author");
        bookInfo.authors = pdfReader.getInfo().get("Author");
        bookInfo.format = "pdf";
        bookInfo.precentRead = 0;

        return bookInfo;
    }

    private void showFirstBookContent(){
        int pageWidth = screenWidth;
        Bitmap bitmap;
        if (numPages <= 20){
            while (nextPageIndex < numPages){
                mCurrentPage = mPdfRenderer.openPage(nextPageIndex);

                try {
                    bitmap = Bitmap.createBitmap(mCurrentPage.getWidth(), mCurrentPage.getHeight(), Bitmap.Config.RGB_565);
                    bitmap.eraseColor(Color.parseColor(defaultBackgroundColor));
                    mCurrentPage.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
                }catch(Exception ex){
                    bitmap = Bitmap.createBitmap(mCurrentPage.getWidth(), mCurrentPage.getHeight(), Bitmap.Config.ARGB_4444);
                    bitmap.eraseColor(Color.parseColor(defaultBackgroundColor));
                    mCurrentPage.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
                }

                mCurrentPage.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
                listPages[nextPageIndex] = bitmap;
                nextPageIndex ++;
                mCurrentPage.close();
            }
        }else{
            while (nextPageIndex < 20){
                mCurrentPage = mPdfRenderer.openPage(nextPageIndex);
                pageWidth = mCurrentPage.getWidth();

                try {
                    bitmap = Bitmap.createBitmap(mCurrentPage.getWidth(), mCurrentPage.getHeight(), Bitmap.Config.RGB_565);
                    bitmap.eraseColor(Color.parseColor(defaultBackgroundColor));
                    mCurrentPage.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
                }catch(Exception ex){
                    bitmap = Bitmap.createBitmap(mCurrentPage.getWidth(), mCurrentPage.getHeight(), Bitmap.Config.ARGB_4444);
                    bitmap.eraseColor(Color.parseColor(defaultBackgroundColor));
                    mCurrentPage.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
                }
                listPages[nextPageIndex] = bitmap;

                nextPageIndex ++;
                mCurrentPage.close();
            }
        }

        setListPdfPagesAdapter(pageWidth);
    }

    private void setListPdfPagesAdapter(int pageWidth){
        //Calculate scale to fit screen
        if(screenWidth > pageWidth)
            scalePage = (float) (screenWidth * 1.0 / pageWidth);
        else
            scalePage= (float) (pageWidth * 1.0 / screenWidth);

        ListPDFViewAdapter.ZoomFactor = scalePage;
        lPDFViewAdapter = new ListPDFViewAdapter(this, listPages);
        listPdfPages.setAdapter(lPDFViewAdapter);
    }

    private void loadNewPDFPage(final int numLoad,  final int type){
        hLoadPDF = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                lPDFViewAdapter.notifyDataSetChanged();
            }
        };

        Thread th = new Thread(){
            @Override
            public void run() {
                int t = 0;
                try {
                    File file = new File(currentBookPath);
                    ParcelFileDescriptor fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                    PdfRenderer pdfRenderer = new PdfRenderer(fileDescriptor);
                    Page newPage;
                    Bitmap bitmap;

                    if (type == 0) {
                        while (t < numLoad) {
                            setBitmapValue(getPrePageIndex(), null);
                            newPage = pdfRenderer.openPage(getNextPageIndex());
                            try {
                                bitmap = Bitmap.createBitmap(newPage.getWidth(), newPage.getHeight(), Bitmap.Config.RGB_565);
                                bitmap.eraseColor(Color.parseColor(defaultBackgroundColor));
                                newPage.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
                            }catch(Exception ex){
                                bitmap = Bitmap.createBitmap(newPage.getWidth(), newPage.getHeight(), Bitmap.Config.ARGB_4444);
                                bitmap.eraseColor(Color.parseColor(defaultBackgroundColor));
                                newPage.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
                            }
                            setBitmapValue(getNextPageIndex(), bitmap);

                            increasePrePage(1);
                            increaseNextPage(1);
                            t++;
                            newPage.close();
                        }
                    } else {
                        while (t < numLoad) {
                            decreaseNextPage(1);
                            decreasePrePage(1);
                            setBitmapValue(getNextPageIndex(), null);
                            newPage = pdfRenderer.openPage(getPrePageIndex());

                            try {
                                bitmap = Bitmap.createBitmap(newPage.getWidth(), newPage.getHeight(), Bitmap.Config.RGB_565);
                                bitmap.eraseColor(Color.parseColor(defaultBackgroundColor));
                                newPage.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
                            }catch(Exception ex){
                                bitmap = Bitmap.createBitmap(newPage.getWidth(), newPage.getHeight(), Bitmap.Config.ARGB_4444);
                                bitmap.eraseColor(Color.parseColor(defaultBackgroundColor));
                                newPage.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
                            }
                            setBitmapValue(getPrePageIndex(), bitmap);

                            t++;
                            newPage.close();
                        }
                    }

                    Message msg = hLoadPDF.obtainMessage();
                    hLoadPDF.sendMessage(msg);
                    fileDescriptor.close();
                    pdfRenderer.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        th.start();
    }

    public synchronized int getPrePageIndex () {
        return prePageIndex;
    }

    public synchronized int getNextPageIndex () {
        return nextPageIndex;
    }

    public synchronized int increaseNextPage(int v) {
        return nextPageIndex += v;
    }

    public synchronized int decreaseNextPage(int v) {
        return nextPageIndex -= v;
    }

    public synchronized int increasePrePage(int v) {
        return prePageIndex += v;
    }

    public synchronized int decreasePrePage(int v) {
        return prePageIndex -= v;
    }

    public synchronized void setBitmapValue(int pos, Bitmap b){
        listPages[pos] = b;
    }

    public void loadPDFBookmark(PdfReader pdfReader){
        listPdfBooknark = new ArrayList<>();
        listBookmarkPosition = new ArrayList<>();
        try
        {
            List<HashMap<String, Object>> bookmarks = SimpleBookmark.getBookmark(pdfReader);
            for(int i=0; i<bookmarks.size(); i++){
                loadChildPDFBookmark(bookmarks.get(i));
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }

        showTableContent();
    }

    public void loadChildPDFBookmark(HashMap<String, Object> bm){
        String title = (String)bm.get("Title");
        listPdfBooknark.add(title);
        String pagePos = ((String)bm.get("Page")).split(" ")[0];
        listBookmarkPosition.add(Integer.parseInt(pagePos));

        List<HashMap<String,Object>> kids = (List<HashMap<String,Object>>)bm.get("Kids");
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                loadChildPDFBookmark(kids.get(i));
            }
        }
    }

    private void showTableContent(){
        if(listPdfBooknark.size() == 0) {
            listPdfBooknark.add("Table of contents is empty");
        }

        chapterAdapter = new ArrayAdapter(context, android.R.layout.simple_list_item_1, listPdfBooknark){
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view;
                if(convertView==null) {
                    LayoutInflater Inflater = (LayoutInflater) PDFContentActivity.this.getSystemService(
                            PDFContentActivity.this.LAYOUT_INFLATER_SERVICE);
                    view = Inflater.inflate(android.R.layout.simple_list_item_1, null);
                }
                else{
                    view=convertView;
                }

                if(position==0){
                    lastChapter = view;
                    view.setBackgroundColor(0xFFC9C7C7);
                }

                TextView txt1=(TextView)view.findViewById(android.R.id.text1);
                txt1.setText(listPdfBooknark.get(position));

                //Rest of your code
                return view;
            }
        };

        leftDrawer.setAdapter(chapterAdapter);
        leftDrawer.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                tableOfChapters.closeDrawers();
                showContentInChapter(view, position);
            }
        });
    }

    private void showContentInChapter(View view, int pos){
        if(lastChapter != null)
            lastChapter.setBackgroundColor(Color.WHITE);
        lastChapter = view;
        view.setBackgroundColor(0xFFD0F79A);
        moveToPage(listBookmarkPosition.get(pos), 2);
    }

    private void moveToPage(int page, int type){
        //type: 1 - first load call, 2-other function call

        int newNextPage, newPrePage, pageWidth = screenWidth;

        listPdfPages.setVisibility(View.INVISIBLE);
        if(numPages - page > 10){
            newNextPage = page + 10;
        }else{
            newNextPage = numPages;
        }

        if(page >= 10){
            newPrePage = page - 10;
        }else{
            newPrePage = 0;
        }

        int t = newPrePage;
        Bitmap bitmap;
        while (t < newNextPage){
            mCurrentPage = mPdfRenderer.openPage(t);
            pageWidth = mCurrentPage.getWidth();

            try {
                bitmap = Bitmap.createBitmap(mCurrentPage.getWidth(), mCurrentPage.getHeight(), Bitmap.Config.RGB_565);
                bitmap.eraseColor(Color.parseColor(defaultBackgroundColor));
                mCurrentPage.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
            }catch(Exception ex){
                bitmap = Bitmap.createBitmap(mCurrentPage.getWidth(), mCurrentPage.getHeight(), Bitmap.Config.ARGB_4444);
                bitmap.eraseColor(Color.parseColor(defaultBackgroundColor));
                mCurrentPage.render(bitmap, null, null, Page.RENDER_MODE_FOR_DISPLAY);
            }

            if(prePageIndex < newPrePage){
                listPages[prePageIndex] = null;
                prePageIndex ++ ;
            }
            listPages[t] = bitmap;

            t ++;
            mCurrentPage.close();
        }
        if(type == 1){
            setListPdfPagesAdapter(pageWidth);
        }else {
            lPDFViewAdapter.notifyDataSetChanged();
        }
        listPdfPages.smoothScrollToPosition(page);
        listPdfPages.setVisibility(View.VISIBLE);
        prePageIndex = newPrePage;
        nextPageIndex = newNextPage;
    }

    private void initSettingList(){
        listSettingText = new ArrayList<>();
        listSettingText.add("Jump to page");
        listSettingText.add("Change background color");
        listSettingText.add("Book information");

        listSettingImage = new ArrayList<>();
        listSettingImage.add(R.drawable.ic_seek_to_page);
        listSettingImage.add(R.drawable.lc_background_color);
        listSettingImage.add(R.drawable.ic_information);
    }

    private void showSettingList(){
        settingAdapter = new ListAppFeaturesAdapter(this, listSettingImage, listSettingText);
        leftDrawer.setAdapter(settingAdapter);
        leftDrawer.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                tableOfChapters.closeDrawers();
                if(position == 0) {
                    showDialogSeekPage();
                }else if(position == 1){
                    showDialogChooseBackgroundColor();
                }else if(position == 2){
                    new AlertDialog.Builder(context)
                    .setIcon(R.drawable.ic_information)
                    .setTitle("Book Information")
                    .setMessage("Title: " + currentBookInfo.title +
                        "\n\nAuthors: " + currentBookInfo.authors +
                        "\n\nFormat: " + currentBookInfo.format +
                        "\n\nBook path: " + currentBookInfo.filePath)
                    .setPositiveButton("OK",  new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
                        }
                    }).show();
                }
            }
        });
    }

    private void showDialogSeekPage(){
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.seek_page_dialog);
        SeekBar seekToPage = (SeekBar) dialog.findViewById(R.id.seekToPage);
        seekToPage.setMax(numPages);
        seekPagePos = 0;

        seekPositionText =  (TextView) dialog.findViewById(R.id.seekPosition);
        seekPositionText.setText("Covered: " + seekToPage.getProgress() + "/" + numPages);

        seekToPage.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progress = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                progress = progresValue;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekPositionText.setText("Covered: " + progress + "/" + seekBar.getMax());
                seekPagePos = progress;
            }
        });

        Button okDialog = (Button) dialog.findViewById(R.id.okDialog);
        okDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
                moveToPage(seekPagePos, 2);
                currentPage = seekPagePos;
            }
        });

        Button cancelDialog = (Button) dialog.findViewById(R.id.cancelDialog);
        cancelDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
            }
        });

        dialog.show();

    }

    private void showDialogChooseBackgroundColor(){
        ArrayList<String> listColor = new ArrayList<>();
        listColor.add("#c9bcbc");
        listColor.add("#b4a298");
        listColor.add("#e3e3e3");
        listColor.add("#000000");
        listColor.add("#ffffff");
        listColor.add("#9ecfc2");
        listColor.add("#718884");
        listColor.add("#8d8d8f");
        listColor.add("#bdbc90");
        listColor.add("#8c8dad");
        listColor.add("#3c3c3c");
        listColor.add("#b1bdac");

        // Custom dialog
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.book_color_dialog);
        ItemTextColorClickInDialog(listColor, dialog);
        Button cancelDialog = (Button) dialog.findViewById(R.id.cancelDialog);
        cancelDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
            }
        });

        dialog.show();
    }

    private void ItemTextColorClickInDialog(final ArrayList<String> listColor, final Dialog dialog){
        ImageButton color1 = (ImageButton) dialog.findViewById(R.id.color1);
        color1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBackgroundColorForLayout(listColor.get(0), dialog);
            }
        });

        ImageButton color2 = (ImageButton) dialog.findViewById(R.id.color2);
        color2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBackgroundColorForLayout(listColor.get(1), dialog);
            }
        });

        ImageButton color3 = (ImageButton) dialog.findViewById(R.id.color3);
        color3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBackgroundColorForLayout(listColor.get(2), dialog);
            }
        });

        ImageButton color4 = (ImageButton) dialog.findViewById(R.id.color4);
        color4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBackgroundColorForLayout(listColor.get(3), dialog);
            }
        });

        ImageButton color5 = (ImageButton) dialog.findViewById(R.id.color5);
        color5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBackgroundColorForLayout(listColor.get(4), dialog);
            }
        });

        ImageButton color6 = (ImageButton) dialog.findViewById(R.id.color6);
        color6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBackgroundColorForLayout(listColor.get(5), dialog);
            }
        });

        ImageButton color7 = (ImageButton) dialog.findViewById(R.id.color7);
        color7.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBackgroundColorForLayout(listColor.get(6), dialog);
            }
        });

        ImageButton color8 = (ImageButton) dialog.findViewById(R.id.color8);
        color8.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBackgroundColorForLayout(listColor.get(7), dialog);
            }
        });

        ImageButton color9 = (ImageButton) dialog.findViewById(R.id.color9);
        color9.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBackgroundColorForLayout(listColor.get(8), dialog);
            }
        });

        ImageButton color10 = (ImageButton) dialog.findViewById(R.id.color10);
        color10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBackgroundColorForLayout(listColor.get(9), dialog);
            }
        });

        ImageButton color11 = (ImageButton) dialog.findViewById(R.id.color11);
        color11.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBackgroundColorForLayout(listColor.get(10), dialog);
            }
        });

        ImageButton color12 = (ImageButton) dialog.findViewById(R.id.color12);
        color12.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBackgroundColorForLayout(listColor.get(11), dialog);
            }
        });
    }

    private void setBackgroundColorForLayout(String color, Dialog dialog){
        //Save epub background color
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("pdfBackgroundColor", color);
        editor.commit();

        dialog.dismiss();
        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);

        defaultBackgroundColor = color;
        moveToPage(currentPage, 2);
    }
}
