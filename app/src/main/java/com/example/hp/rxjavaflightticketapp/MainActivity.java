package com.example.hp.rxjavaflightticketapp;

import android.content.res.Resources;
import android.graphics.Color;
import android.net.sip.SipRegistrationListener;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.example.hp.rxjavaflightticketapp.adapter.TicketAdapter;
import com.example.hp.rxjavaflightticketapp.model.Price;
import com.example.hp.rxjavaflightticketapp.model.Ticket;
import com.example.hp.rxjavaflightticketapp.network.ApiClient;
import com.example.hp.rxjavaflightticketapp.network.ApiService;
import com.example.hp.rxjavaflightticketapp.utils.GridSpacingItemDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements TicketAdapter.TicketsAdapterListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String from = "DEL";
    private static final String to = "HYD";

    private CompositeDisposable disposable = new CompositeDisposable();
    private Unbinder unbinder;

    private ApiService apiService;
    private TicketAdapter mAdapter;
    private ArrayList<Ticket> ticketsList = new ArrayList<>();

    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.relativeLayout)
    RelativeLayout relativeLayout;


    LinearLayoutManager linearLayoutManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        unbinder = ButterKnife.bind(this);

        apiService = ApiClient.getClient().create(ApiService.class);

        mAdapter = new TicketAdapter(this, ticketsList, this);

        linearLayoutManager = new GridLayoutManager(this, 1);
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(1, dpToPx(5), true));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);

        final ConnectableObservable<List<Ticket>> ticketObservable = getTickets(from,to).replay();
        disposable.add(
                ticketObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableObserver<List<Ticket>>() {
                    @Override
                    public void onNext(List<Ticket> tickets) {
                         ticketsList.clear();
                         ticketsList.addAll(tickets);
                          mAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(Throwable e) {
                      showError(e);
                    }

                    @Override
                    public void onComplete() {

                    }
                })
        );



        disposable.add(
                ticketObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(new Function<List<Ticket>, ObservableSource<Ticket>>() {
                    @Override
                    public ObservableSource<Ticket> apply(List<Ticket> tickets) throws Exception {
                        return io.reactivex.Observable.fromIterable(tickets);
                    }
                }).flatMap(new Function<Ticket, ObservableSource<Ticket>>() {
                    @Override
                    public ObservableSource<Ticket> apply(Ticket ticket) throws Exception {
                        return getPriceObservable(ticket);
                    }
                })
                .subscribeWith(new DisposableObserver<Ticket>() {
                    @Override
                    public void onNext(Ticket ticket) {
                         int position  = ticketsList.indexOf(ticket);
                         if (position == -1){
                             return;
                         }

                         ticketsList.set(position,ticket);
                         mAdapter.notifyItemChanged(position);
                    }

                    @Override
                    public void onError(Throwable e) {
                         showError(e);
                    }

                    @Override
                    public void onComplete() {

                    }
                }));

        ticketObservable.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbinder.unbind();
        disposable.dispose();
    }



    private io.reactivex.Observable<List<Ticket>> getTickets(String from, String to){
        return apiService.searchTickets(from,to)
                .toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private io.reactivex.Observable<Ticket> getPriceObservable(final Ticket ticket){
        return apiService.getPrice(ticket.getFlightNumber(),ticket.getFrom(),ticket.getTo())
                .toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map(new Function<Price, Ticket>() {
                    @Override
                    public Ticket apply(Price price) throws Exception {
                        ticket.setPrice(price);
                        return ticket;
                    }
                });
    }

    @Override
    public void onTicketSelected(Ticket contact) {

    }

    private void showError(Throwable e) {
        Log.e(TAG, "showError: " + e.getMessage());

        Snackbar snackbar = Snackbar
                .make(relativeLayout, e.getMessage(), Snackbar.LENGTH_LONG);
        View sbView = snackbar.getView();
        TextView textView = sbView.findViewById(android.support.design.R.id.snackbar_text);
        textView.setTextColor(Color.YELLOW);
        snackbar.show();
    }


    private int dpToPx(int dp) {
        Resources r = getResources();
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics()));
    }
}
