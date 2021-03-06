package mini.student.quiz.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import mini.student.quiz.R;
import mini.student.quiz.adapter.QuestionAdapter;
import mini.student.quiz.callback.OnCautionDialogListener;
import mini.student.quiz.callback.OnQuizListener;
import mini.student.quiz.dialog.CautionDialog;
import mini.student.quiz.dialog.ListQuestionDialog;
import mini.student.quiz.dialog.ResultDialog;
import mini.student.quiz.model.Exam;
import mini.student.quiz.model.History;
import mini.student.quiz.model.Question;
import mini.student.quiz.model.User;
import mini.student.quiz.utils.DBAssetHelper;
import mini.student.quiz.utils.DBHelper;
import mini.student.quiz.utils.IOHelper;
import mini.student.quiz.utils.Pref;
import mini.student.quiz.utils.QuizHelper;
import mini.student.quiz.utils.Units;

public class QuizActivity extends AppCompatActivity implements View.OnClickListener,
        OnQuizListener, OnCautionDialogListener {

    public static final String TYPE = "EXAM_TYPE";
    public static final String EXAM = "EXAM_MODEL";
    public static final String STATUS = "STATUS";
    public static final String IDGV = "IDGV";
    public static final String IDGV2 = "IDGV2";
    public static String TYPE2 = "TYPE2";

    private boolean isLoading;

    private Bundle bundle;
    private String type;
    private String type2="xx";
    private Exam exam;
    private String idGV;
    private String idGV2;

    private DBAssetHelper dbAssetHelper;
    private DBHelper dbHelper;
    private Pref pref;

    private ArrayList<Question> questions;
    private int currentPage;
    /*
    * case: 0- b???t ?????u thi
    * case: 1- ch??? xem ??i???m
    * case: 2- xem ????p ??n
    * */
    private int status;

    private long timeInMillis;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbAssetHelper = new DBAssetHelper(this);
        dbHelper = new DBHelper(this);
        pref = new Pref(this);
        bundle = getIntent().getExtras();
        if (bundle != null) {
            type = bundle.getString(TYPE);
            type2 = bundle.getString(TYPE2);
            exam = bundle.getParcelable(EXAM);
            idGV = bundle.getString(IDGV);
            idGV2 = bundle.getString(IDGV2);
        }
        setContentView(R.layout.activity_quiz);
        initWidgets();
        if (savedInstanceState == null){
            isLoading = true;
            questions = bundle.getParcelableArrayList("questions");
            currentPage = 0;
            timeInMillis = exam.getTime() * 60 * 1000;
            status = bundle.getInt(STATUS, 0);
        } else {
            isLoading = savedInstanceState.getBoolean("loading");
            questions = savedInstanceState.getParcelableArrayList("questions");
            currentPage = savedInstanceState.getInt("page");
            timeInMillis = savedInstanceState.getLong("time_in_millis");
            status = savedInstanceState.getInt("status");
        }

        if (questions != null && questions.size() > 0){
            showQuestions();
            if (savedInstanceState == null && status != 0){
                showResultDialog();
            }
        } else {
            loadQuestions();
        }
    }

    /**
     * @return thay ?????i tr???ng th??i sau khi l??m b??i
     * n???u exam-status (firebase) = 0 -> status = 1
     * n???u exam-status (firebase) = 1 -> status = 2
     */
    private int toggleStatus(int examStatus){
        if (examStatus == 0)
            return 1;
        else return 2;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("loading", isLoading);
        outState.putParcelableArrayList("questions", questions);
        outState.putInt("page", currentPage);
        outState.putLong("time_in_millis", timeInMillis);
        outState.putInt("status", status);
    }

    private TextView tvEnd;
    private TextView tvTitle;
    private RelativeLayout layoutBody;
    private ProgressBar progressBar;
    private void initWidgets(){
        tvTitle = findViewById(R.id.tv_title);
        layoutBody = findViewById(R.id.layout_body);
        progressBar = findViewById(R.id.progressbar);
        tvEnd = findViewById(R.id.tv_end);
        tvEnd.setOnClickListener(this);
    }

    /*Hi???n th??? c??u h???i*/
    private QuestionAdapter adapter;
    private void showQuestions(){
        layoutBody.removeAllViews();
        addControlLayout();
        addViewPager();
        adapter = new QuestionAdapter(this, status, questions);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(currentPage);
        initControl(currentPage);
        viewPager.addOnPageChangeListener(onPageChange);
        goneViews(progressBar);
        initEnd();
        initTitle();
        isLoading = false;
    }

    //C???u h??nh n??t tho??t
    private void initEnd(){
        if (status == 0 && questions != null && questions.size() > 0){
            //N???u ??ang thi
            tvEnd.setText(R.string.end_quiz);
            tvEnd.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_timer_off, 0, 0, 0);
        } else {
            tvEnd.setText(R.string.exit);
            tvEnd.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_close, 0, 0, 0);
        }
    }

    //C???u h??nh tv_title
    //N???u ??ang thi -> Hi???n th??? b??? ?????m th???i gian
    //N???u ??ang xem k???t qu??? -> Hi???n th??? ??i???m
    private final Handler timer = new Handler();
    private void initTitle(){
        if (status != 0){
            tvTitle.setText(Html.fromHtml("??i???m thi: <u>" +
                    QuizHelper.getStringScore(QuizHelper.getScore(questions)) + "</u>"));
            tvTitle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //Hi???n th??? Dialog th??ng b??o ??i???m
                    showResultDialog();
                }
            });
        } else {
            tvTitle.setText(QuizHelper.getTimeCountdown(timeInMillis));
            timer.removeCallbacksAndMessages(null);
            timer.postDelayed(new Runnable() {
                @Override
                public void run() {
                    timeInMillis = timeInMillis - 1000;
                    if (timeInMillis != 0){
                        tvTitle.setText(QuizHelper.getTimeCountdown(timeInMillis));
                        timer.postDelayed(this, 1000);
                    } else {
                        tvTitle.setText(R.string.end_time);
                        status = toggleStatus(exam.getStatus());
                        showResult();
                    }
                }
            }, 1000);
        }
    }

    private void showResultDialog(){
        ResultDialog dialog = ResultDialog.newInstance(exam, questions);
        dialog.show(getSupportFragmentManager(), dialog.getTag());
    }

    /*T???o ViewPager hi???n th??? c??u h???i*/
    private ViewPager viewPager;
    private void addViewPager(){
        viewPager = new ViewPager(this);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT
        );
        params.addRule(RelativeLayout.ABOVE, LAYOUT_CONTROL_ID);
        layoutBody.addView(viewPager, params);
    }

    private void initControl(int page){
        if (adapter.getCount() <= 1){
            invisibleViews(btNext, btPrevious);
        } else {
            if (page == 0){
                invisibleViews(btPrevious);
                visibleViews(btNext);
            } else if (page == adapter.getCount() - 1){
                invisibleViews(btNext);
                visibleViews(btPrevious);
            } else {
                visibleViews(btNext, btPrevious);
            }
        }
        tvPageCount.setText(String.format(Locale.getDefault(), "C??u %d/%d",
                page + 1, adapter.getCount()));
    }

    /*T???o b???ng ??i???u khi???n c??u h???i*/
    private ImageButton btPrevious;
    private ImageButton btNext;
    private TextView tvPageCount;
    private static final int LAYOUT_CONTROL_ID = 1;
    private static final int BT_PREVIOUS_ID = 2;
    private static final int BT_NEXT_ID = 3;
    private static final int TV_PAGE_COUNT_ID = 4;
    private void addControlLayout(){
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, Units.dpToPx(48)
        );
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        RelativeLayout layoutControl = new RelativeLayout(this);
        layoutControl.setId(LAYOUT_CONTROL_ID);
        layoutControl.setBackgroundResource(R.drawable.bg_border_top);
        //TODO: Add button Previous
        RelativeLayout.LayoutParams params1 = new RelativeLayout.LayoutParams(
                Units.dpToPx(40), Units.dpToPx(40)
        );
        btPrevious = new ImageButton(this);
        btPrevious.setId(BT_PREVIOUS_ID);
        initButton(btPrevious, R.drawable.ic_previous);

        //TODO: Add button Next
        RelativeLayout.LayoutParams params2 = new RelativeLayout.LayoutParams(
                Units.dpToPx(40), Units.dpToPx(40)
        );
        btNext = new ImageButton(this);
        btNext.setId(BT_NEXT_ID);
        initButton(btNext, R.drawable.ic_next);
        params2.addRule(RelativeLayout.ALIGN_PARENT_END);
        params2.addRule(RelativeLayout.CENTER_VERTICAL);

        //TODO: Add TextView pageCount
        RelativeLayout.LayoutParams params3 = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        tvPageCount = new TextView(this);
        tvPageCount.setId(TV_PAGE_COUNT_ID);
        tvPageCount.setBackgroundResource(R.drawable.bg_page_count);
        tvPageCount.setTextColor(getResources().getColor(R.color.colorPrimary));
        tvPageCount.setPadding(Units.dpToPx(8), Units.dpToPx(8), Units.dpToPx(8), Units.dpToPx(8));
        tvPageCount.setGravity(Gravity.CENTER);
        tvPageCount.setOnClickListener(this);
        tvPageCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvPageCount.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_list, 0);
        tvPageCount.setCompoundDrawablePadding(Units.dpToPx(8));
        tvPageCount.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        params3.addRule(RelativeLayout.CENTER_IN_PARENT);

        layoutControl.addView(btPrevious, params1);
        layoutControl.addView(btNext, params2);
        layoutControl.addView(tvPageCount, params3);

        layoutBody.addView(layoutControl, params);
    }

    private void showErrorPage(String err){
        TextView textView = new TextView(this);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setTextColor(getResources().getColor(R.color.black));
        textView.setText(Html.fromHtml(err));
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        layoutBody.removeAllViews();
        layoutBody.addView(textView, params);
        goneViews(progressBar);
    }

    private ViewPager.OnPageChangeListener onPageChange = new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

        }

        @Override
        public void onPageSelected(int position) {
            //On item change
            currentPage = position;
            initControl(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.tv_end: onBackPressed(); break;
            case BT_PREVIOUS_ID: gotoPage(currentPage - 1); break;
            case BT_NEXT_ID: gotoPage(currentPage + 1); break;
            case TV_PAGE_COUNT_ID: showListQuestionDialog(); break;
        }
    }

    private void gotoPage(int position){
        viewPager.setCurrentItem(position);
    }

    private void showListQuestionDialog(){
        ListQuestionDialog dialog = ListQuestionDialog.newInstance(status, questions);
        dialog.show(getSupportFragmentManager(), dialog.getTag());
    }

    @Override
    public void onQuestionChange(int position) {
        gotoPage(position);
    }

    @Override
    public void onBackPressed() {
        if (!isLoading){
            if (questions != null && questions.size() > 0) {
                showCautionDialog();
            } else {
                super.onBackPressed();
            }
        }
    }

    private void showCautionDialog(){
        String tag;
        String title;
        String message;
        if (status == 0){
            tag = "endPlay";
            title = "N???p b??i";
            message = "B???n c?? mu???n d???ng thi ngay b??y gi????";
        } else {
            tag = "exit";
            title = "Tho??t";
            message = "B???n c?? mu???n tho??t?";
        }
        CautionDialog dialog = CautionDialog.newInstance(tag, title, message);
        dialog.show(getSupportFragmentManager(), tag);
    }

    @Override
    public void onConfirm(String tag) {
        if (tag.equals("endPlay")){
            showResult();
        }
        finish();
    }

    private void showResult(){
        updateHistory();
        Bundle bundle = new Bundle();
        bundle.putString(TYPE, type);
        exam.setLastHistory(lastHistory());
        bundle.putParcelable(EXAM, exam);
        bundle.putInt(STATUS, toggleStatus(exam.getStatus()));
        bundle.putParcelableArrayList("questions", questions);
        Intent intent = new Intent(getApplicationContext(), QuizActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    private History lastHistory(){
        History history = new History();
        history.setType(type);
        history.setName(exam.getName());
        history.setExamID(exam.getId());
        history.setSubjectID(exam.getSubjectID());
        history.setScore(QuizHelper.getScore(questions));
        history.setSubmitted(System.currentTimeMillis());
        history.setTimePlay(exam.getTime() * 60 * 1000 - timeInMillis);
        history.setChoice(QuizHelper.setChoice(questions));
        return history;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.no_animation, R.anim.scale_out);
    }

    @Override
    protected void onStop() {
        super.onStop();
        bundle.clear();
        timer.removeCallbacksAndMessages(null);
        //G???i th??ng ??i???p ????? thay ?????i giao di???n luy???n t???p
        if (status == 0 && type.equals("practice")) {
            EventBus.getDefault().post(exam);
        }
    }

    //L??u k???t qu??? thi
    private DatabaseReference myRef;
    private void updateHistory(){
        //L??u k???t qu??? v??o database
        dbHelper.insertHistory(lastHistory());
        if (type.equals("exam")){
            //N???u ??ang thi online -> L??u k???t qu??? l??n Firebase
            myRef = FirebaseDatabase.getInstance().getReference("histories");
            myRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    //L??u l???ch s??? thi t???i ???????ng d???n: histories/UID/th???i gian thi
                    String path = pref.getData(Pref.UID) + "/" + lastHistory().getSubmitted();
                    myRef.child(path).child("subjectID").setValue(exam.getSubjectID());
                    myRef.child(path).child("type").setValue(type);
                    myRef.child(path).child("examID").setValue(exam.getId());
                    myRef.child(path).child("timePlay").setValue(lastHistory().getTimePlay());
                    myRef.child(path).child("score").setValue(lastHistory().getScore());
                    myRef.child(path).child("choice").setValue(lastHistory().getChoice());
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });
        }
    }

    /**Helper**/
    private void loadQuestions(){
        if (type.equals("practice")){
            //T???i ????? thi t??? database tr??n assets
            questions = dbAssetHelper.questions(exam.getId());
            if (questions.size() > 0){
                //C???p nh???t l???i c??u h???i
                updateQuestions();
                //Hi???n th??? c??u h???i
                showQuestions();
                if (status != 0){
                    showResultDialog();
                }
            } else {
                showErrorPage("Ch??a c?? c??u h???i cho ph???n ??n luy???n n??y");
                initEnd();
            }
        } else {
            if (status == 2 && type.equals("exam")){
                loadHistoryStatus();
            } else {
                loadJsonFile();
            }
        }
        isLoading = false;
    }

    //T???i l???i tr???ng th??i xem k???t qu??? tr??n firebase (trong TH ?????c l???ch s??? thi)
    //M???c ?????nh khi xem l???ch s??? thi s??? ????? l?? 2
    //Nh??ng n???u t???i l???ch s??? thi c???a "exam" th?? t???i l???i xem ng?????i d??ng c?? ???????c xem ????p ??n hay kh??ng
    private void loadHistoryStatus(){
        DatabaseReference myRef = FirebaseDatabase.getInstance().getReference().child("exams");
        myRef.child(String.valueOf(exam.getSubjectID())).child(String.valueOf(exam.getId()))
                .addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        try {
                            Exam exam = dataSnapshot.getValue(Exam.class);
                            status = toggleStatus(exam.getStatus());
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                        loadJsonFile();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                }
        );
    }

    //T???i ????? thi t??? file l??u tr??n th?? m???c firebase
    private void loadJsonFile(){
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        String path;
            if (type.equals("exam")){
                //Toast.makeText(this, type2, Toast.LENGTH_SHORT).show();
                if(type2!=null)
                {
                    if(type2.equals("exam2"))
                    {
                        String[] words= exam.getName().split("\\s");
                        path = "onluyen/" + exam.getSubjectID() + "/"+words[0]+"/" + exam.getId() + ".json";
                        //Toast.makeText(QuizActivity.this, path, Toast.LENGTH_SHORT).show();
                    }
                    else
                    {
                        //Toast.makeText(this, exam.getName(), Toast.LENGTH_SHORT).show();
                        String[] words= exam.getName().split("\\s");
                        //Toast.makeText(this, "Chuoi cat: "+words[0]+"|"+words[1]+"|"+words[2], Toast.LENGTH_LONG).show();
                        path = "exams/" + exam.getSubjectID() + "/"+words[0]+"/" + exam.getId() + ".json";
                        //Toast.makeText(QuizActivity.this, type2+path, Toast.LENGTH_SHORT).show();
                    }
                }
                else
                {
                    String[] words= exam.getName().split("\\s");
                    path = "onluyen/" + exam.getSubjectID() + "/"+words[0]+"/" + exam.getId() + ".json";
                }
            } else {
                path = "upload/" + pref.getData(Pref.UID) + "/" + exam.getId() + ".json";
            }

            storageRef.child(path).getDownloadUrl()
                    .addOnSuccessListener(new OnSuccessListener<Uri>() {
                        @Override
                        public void onSuccess(Uri uri) {
                            readJsonFile(uri.toString());
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            if(type2==null)
                            {
                                showErrorPage("<b>B???n kh??ng th??? xem l???i ????? thi!</b>");
                                initEnd();
                            }
                            else {
                                showErrorPage("<b>L???i:</b> Kh??ng t??m th???y File c??u h???i");
                                initEnd();
                            }
                        }
                    });
    }

    private void readJsonFile(String url){
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        String s = response.toString();
                        if (s != null){
                            questions = IOHelper.questions(s);
                            if (questions.size() > 0){
                                updateQuestions();
                                showQuestions();
                                if (status != 0){
                                    showResultDialog();
                                }
                            } else {
                                showErrorPage("<b>L???i</b>: ?????nh d???ng file kh??ng ????ng");
                                initEnd();
                            }
                        } else {
                            showErrorPage("Ch??a c?? c??u h???i cho ????? thi n??y");
                            initEnd();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        showErrorPage("<b>L???i</b>: ?????nh d???ng file kh??ng ????ng");
                        initEnd();
                    }
                });
        requestQueue.add(request);
    }

    //C???p nh???t l???i c??u h???i sau khi t???i
    private void updateQuestions(){
        if (status == 0){
            //N???u b???t ?????u thi -> ?????o v??? tr?? ng???u nhi??n c??c c??u h???i
            Collections.shuffle(questions);
        } else {
            //N???u xem k???t qu??? -> C???p nh???t c??u tr??? l???i c???a ng?????i d??ng
            questions = QuizHelper.historyQuestions(exam.getLastHistory().getChoice(), questions);
        }
    }

    private void goneViews(final View... views) {
        if (views != null && views.length > 0) {
            for (View view : views) {
                if (view != null) {
                    view.setVisibility(View.GONE);
                }
            }
        }
    }

    private void initButton(ImageButton button, int icon){
        button.setImageResource(icon);
        button.setBackgroundResource(R.drawable.bg_button_circle_white);
        button.setOnClickListener(this);
    }

    protected void visibleViews(final View... views) {
        if (views != null && views.length > 0) {
            for (View view : views) {
                if (view != null) {
                    view.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    protected void invisibleViews(final View... views) {
        if (views != null && views.length > 0) {
            for (View view : views) {
                if (view != null) {
                    view.setVisibility(View.INVISIBLE);
                }
            }
        }
    }
}
