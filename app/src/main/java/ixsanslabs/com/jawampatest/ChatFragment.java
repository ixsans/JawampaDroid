package ixsanslabs.com.jawampatest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ixsanslabs.com.jawampatest.data.Constants;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import ws.wamp.jawampa.WampClient;
import ws.wamp.jawampa.WampClientBuilder;
import ws.wamp.jawampa.WampError;
import ws.wamp.jawampa.connection.IWampConnectorProvider;
import ws.wamp.jawampa.transport.netty.NettyWampClientConnectorProvider;


public class ChatFragment extends Fragment {
    private static final String TAG = "IX";

    private ImageView sendButton;
    private RecyclerView mMessagesView;
    private EditText editTextMessage;
    private List<Message> mMessages = new ArrayList<Message>();
    private RecyclerView.Adapter mAdapter;
    private ProgressDialog progressDialog;
    private TextView textStatus;

    WampClient client;
    IWampConnectorProvider connectorProvider = new NettyWampClientConnectorProvider();
    Subscription addProcSubscription;
    Subscription counterPublication;
    Subscription onHelloSubscription;

    // Scheduler for this example
    ExecutorService executor = Executors.newSingleThreadExecutor();

    private String mUsername;
  

    public ChatFragment() {
        super();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mAdapter = new MessageAdapter(activity, mMessages);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mUsername = getActivity().getIntent().getStringExtra("username");
        if(mUsername == null) {
            getActivity().finish();
        }

       connect();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view =  inflater.inflate(R.layout.fragment_chat, container, false);

        //initialize recyclerview
        mMessagesView = (RecyclerView) view.findViewById(R.id.messages);
        mMessagesView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mMessagesView.setAdapter(mAdapter);

        editTextMessage = (EditText) view.findViewById(R.id.edit_text_message);
        editTextMessage.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, KeyEvent event) {
                if (id == R.id.send || id == EditorInfo.IME_NULL) {
                    attemptSend();
                    return true;
                }
                return false;
            }
        });

        sendButton = (ImageView) view.findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptSend();
            }
        });

        textStatus = (TextView) view.findViewById(R.id.text_status);
        editTextMessage = (EditText) view.findViewById(R.id.edit_text_message);

        return view;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_leave) {
            leave();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void addLog(String message) {
        mMessages.add(new Message.Builder(Message.TYPE_LOG)
                .message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }


    private void addMessage(String username, String message) {
        mMessages.add(new Message.Builder(Message.TYPE_MESSAGE)
                .username(username).message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    
    private void connect() {
        progressDialog = ProgressDialog.show(getActivity(), "", "Connecting...", false, true);
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                getActivity().finish();
            }
        });
        WampClientBuilder builder = new WampClientBuilder();
        try {
            builder.withUri(Constants.CHAT_SERVER_URL)
                    .withRealm(Constants.REALM)
                    .withInfiniteReconnects()
                    .withConnectorProvider(connectorProvider)
                    .withCloseOnErrors(true)
                    .withReconnectInterval(5, TimeUnit.SECONDS);
            client = builder.build();

        } catch (WampError e) {
            e.printStackTrace();
            return;
        }catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // Subscribe on the clients status updates
        client.statusChanged()
                .subscribeOn(Schedulers.newThread()) // Create a new Thread
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<WampClient.State>() {
                    @Override
                    public void call(WampClient.State t1) {
                        System.out.println("Session status changed to " + t1);

                        if (t1 instanceof WampClient.ConnectedState) {
                            Log.d(TAG, "Connected!");
                            progressDialog.dismiss();
                            textStatus.setText("Connected.");
                            addLog(getResources().getString(R.string.message_welcome));

                            subscribeMessage();

                        } else if (t1 instanceof WampClient.ConnectingState) {
                            textStatus.setText("Connecting...");
                        } else if (t1 instanceof WampClient.DisconnectedState) {
                            closeSubscriptions();
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable t) {
                        progressDialog.dismiss();
                        getActivity().finish();
                        Toast.makeText(getActivity(), "Failed to connect server", Toast.LENGTH_SHORT).show();
                       Log.d(TAG, "Session ended with error " + t);
                    }
                }, new Action0() {
                    @Override
                    public void call() {
                        progressDialog.dismiss();
                        getActivity().finish();
                       Log.d(TAG, "Session ended normally");
                    }
                });

        client.open();
    }

    /**
     * Close all subscriptions (registered events + procedures)
     * and shut down all timers (doing event publication and calls)
     */
    void closeSubscriptions() {
        if (onHelloSubscription != null)
            onHelloSubscription.unsubscribe();
        onHelloSubscription = null;
        if (counterPublication != null)
            counterPublication.unsubscribe();
        counterPublication = null;
        if (addProcSubscription != null)
            addProcSubscription.unsubscribe();
        addProcSubscription = null;
    }



    private void subscribeMessage() {
        // SUBSCRIBE to a topic and receive events
        onHelloSubscription = client.makeSubscription(Constants.EVENT_NEW_MESSAGE, String.class)
                .subscribeOn(Schedulers.newThread()) // Create a new Thread
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String incommingMessage) {
                       // Toast.makeText(getActivity(), "new message", Toast.LENGTH_SHORT).show();
                        JSONObject data;
                        try {
                            data = new JSONObject(incommingMessage);

                            final String username;
                            final String message;
                            username = data.optString("username");
                            message = data.optString("message");

                            Log.d(TAG, "message received: " + incommingMessage);
                            addMessage(username, message);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        

                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable e) {
                       Log.d(TAG, "failed to subscribe:"+Constants.EVENT_NEW_MESSAGE +"->" + e);
                    }
                }, new Action0() {
                    @Override
                    public void call() {
                        Log.d(TAG, "subcription " + Constants.EVENT_NEW_MESSAGE + " ended");
                    }
                });
    }


    private void attemptSend()  {

        final String message = editTextMessage.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            editTextMessage.requestFocus();
            return;
        }

         final JSONObject jsonMessage = new JSONObject();
        try {
            jsonMessage.put("username", mUsername);
            jsonMessage.put("message", message);

            editTextMessage.setText("");
            addMessage(mUsername, message);
        Toast.makeText(getActivity(), "SEND", Toast.LENGTH_SHORT).show();
String s = jsonMessage.toString();
             //final String msg = "{\"username\": \""+mUsername +"\", \"message\":\""+message+"\"}";
            client.publish(Constants.EVENT_NEW_MESSAGE, s)
                    .subscribeOn(Schedulers.newThread()) // Create a new Thread
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<Long>() {
                        @Override
                        public void call(Long t1) {
                          //  Log.d(TAG, "publish message: " + jsonMessage.toString());
                        }
                    }, new Action1<Throwable>() {
                        @Override
                        public void call(Throwable e) {
                            Log.d(TAG, "Error during publishing message: " + e);
                        }
                    });
        Log.d(TAG, "publish...");
         } catch (JSONException e) {
            e.printStackTrace();
        }

    }


    private void leave() {
        Log.d(TAG, "disconnect socket");
        closeSubscriptions();
        client.close();
        try {
            client.getTerminationFuture().get();
        } catch (Exception e) {
        }

        executor.shutdown();
        mUsername = null;
        getActivity().finish();
    }

    private void scrollToBottom() {
        mMessagesView.scrollToPosition(mAdapter.getItemCount() - 1);
    }


}

