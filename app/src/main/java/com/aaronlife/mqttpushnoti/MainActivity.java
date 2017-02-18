package com.aaronlife.mqttpushnoti;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;


public class MainActivity extends AppCompatActivity
{
    public static final String LOGTAG = "aarontest";

    private TextView txtMessage;
    private ScrollView scrollView;
    
    private EditText brokerSubHost, brokerPubHost, brokerSubPort, brokerPubPort;
    private EditText subId, pubId;
    private EditText subTopic, pubTopic, pubPayload;
    private Spinner spnQosSub, spnQosPub;

    private MqttAndroidClient subClient, pubClient;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();

        // 顯示版本名稱
        PackageInfo pInfo = null;
        try
        {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        }
        catch(PackageManager.NameNotFoundException e)
        {
            e.printStackTrace();
        }
        txtMessage.setText("Version: " + pInfo.versionName);

        listLocalIpAddress();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        
        loadSettings();

        // 移動游標到文字最後面
        brokerSubHost.setSelection(brokerSubHost.getText().length());
    }
    
    @Override
    protected void onPause()
    {
        super.onPause();
        
        saveSettings();
    }
    
    public String listLocalIpAddress() 
    {
        try 
        {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) 
            {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) 
                {
                    InetAddress inetAddress = enumIpAddr.nextElement();

                    addMessage("Local IP: " + inetAddress.getHostAddress());

//                    if (!inetAddress.isLoopbackAddress())
//                    {
//                        return inetAddress.getHostAddress();
//                    }
                }
            }
        } 
        catch (SocketException e)
        {
            addMessage("Get IP exception: " + e.getMessage());
        }
        
        return "";
    }

    protected void initUI()
    {
        txtMessage = (TextView)findViewById(R.id.message);
        scrollView = (ScrollView)findViewById(R.id.scrollview);
        
        brokerSubHost = (EditText)findViewById(R.id.broker_sub_host);
        brokerPubHost = (EditText)findViewById(R.id.broker_pub_host);
        brokerSubPort = (EditText)findViewById(R.id.broker_sub_port);
        brokerPubPort = (EditText)findViewById(R.id.broker_pub_port);
        subId = (EditText)findViewById(R.id.sub_id);
        pubId = (EditText)findViewById(R.id.pub_id);
        subTopic = (EditText)findViewById(R.id.sub_topic);
        pubTopic = (EditText)findViewById(R.id.pub_topic);
        pubPayload = (EditText)findViewById(R.id.pub_payload);

        ArrayAdapter<CharSequence> adapterQos = ArrayAdapter.createFromResource(this,
                R.array.qos_array, android.R.layout.simple_spinner_item);
        adapterQos.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spnQosPub = (Spinner)findViewById(R.id.pub_qos);
        spnQosPub.setAdapter(adapterQos);

        spnQosSub = (Spinner)findViewById(R.id.sub_qos);
        spnQosSub.setAdapter(adapterQos);
    }

    public void onSubscribe(final View v)
    {
        final String topic  = subTopic.getText().toString();
        final int qos       = spnQosSub.getSelectedItemPosition();
        String broker       = brokerSubHost.getText().toString() + ":" +
                              brokerSubPort.getText().toString();
        String clientId     = subId.getText().toString();

        if(subClient != null && subClient.isConnected())
        {
            addMessage("Subscriber connected.");

            try
            {
                subClient.unsubscribe(topic);
                subClient.disconnect();
                subClient = null;
            }
            catch(MqttException e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            addMessage("Subscriber not connected.");

            MemoryPersistence persistence = new MemoryPersistence();

            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            subClient = new MqttAndroidClient(this, broker, clientId, persistence);

            try
            {
                subClient.setCallback(new MqttCallback()
                {
                    @Override
                    public void connectionLost(Throwable throwable)
                    {
                        addMessage("Subscriber: connectionLost.");

                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                ((Button)v).setText("Subscribe");
                            }
                        });
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception
                    {
                        addMessage("Subscriber: messageArrived: " + topic + "-" + mqttMessage.toString());
                        showNotification(topic, mqttMessage.toString());
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken)
                    {
                        addMessage("Subscriber: deliveryComplete.");
                    }
                });

                subClient.connect(connOpts, null, new IMqttActionListener()
                {
                    @Override
                    public void onSuccess(IMqttToken iMqttToken)
                    {
                        addMessage("Subscriber connected.");
                        ((Button)v).setText("Disconnect");

                        addMessage("Subscriber: onSuccess.");

                        try
                        {
                            subClient.subscribe(topic, qos); //, new IMqttMessageListener()
//                        {
//                            @Override
//                            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception
//                            {
//                                addMessage("subscribe: " + topic + " - " + mqttMessage.toString());
//                            }
//                        });
                        }
                        catch(MqttException e)
                        {
                            addMessage("Subscriber connect exception: " + e.getMessage());
                            addMessage("cause " + e.getCause());
                            addMessage("excep " + e);
                        }
                    }

                    @Override
                    public void onFailure(IMqttToken iMqttToken, Throwable throwable)
                    {
                        addMessage("Subscriber: onFailure: " + throwable.getMessage());
                        addMessage("cause " + throwable.getCause());
                        addMessage("excep " + throwable);
                    }
                });
            }
            catch(MqttException e)
            {
                addMessage("Subscriber exception: " + e.getMessage());
                addMessage("cause " + e.getCause());
                addMessage("excep " + e);
            }
        }


        /*
        try
        {
            MqttClient sampleClient = new MqttClient(broker, clientId, persistence);
            addMessage("Connecting to broker: "+broker);
            sampleClient.connect(connOpts);
            addMessage("Connected");
            addMessage("Publishing message: "+content);

            sampleClient.subscribe(topic, qos, new IMqttMessageListener()
            {
                @Override
                public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception
                {
                    addMessage(topic + ": " + mqttMessage.toString());
                }
            });
            addMessage("Message published");

            try
            {
                Thread.sleep(1000);
            }
            catch(InterruptedException e)
            {
                e.printStackTrace();
            }
            sampleClient.publish(topic, message);

            //sampleClient.disconnect();
            addMessage("Disconnected");

        }
        catch(MqttException me)
        {
            addMessage("reason "+me.getReasonCode());
            addMessage("msg "+me.getMessage());
            addMessage("loc "+me.getLocalizedMessage());
            addMessage("cause "+me.getCause());
            addMessage("excep "+me);
            me.printStackTrace();
        }
        */
    }

    public void onPublish(final View v)
    {
        final String topic  = pubTopic.getText().toString();
        String payload      = pubPayload.getText().toString();
        final int qos       = spnQosPub.getSelectedItemPosition();
        String broker       = brokerPubHost.getText().toString() + ":" + brokerPubPort.getText().toString();
        String clientId     = pubId.getText().toString();

        MemoryPersistence persistence = new MemoryPersistence();

        final MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(qos);

        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);

        pubClient = new MqttAndroidClient(this, broker, clientId, persistence);

        if(pubClient.isConnected())
            addMessage("Publisher connected.");
        else
            addMessage("Publisher not connected.");

        try
        {
            pubClient.setCallback(new MqttCallback()
            {
                @Override
                public void connectionLost(Throwable throwable)
                {
                    addMessage("Publisher: connectionLost.");

                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            v.setEnabled(true);
                        }
                    });
                }

                @Override
                public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception
                {
                    addMessage("Publisher: messageArrived: " + topic + " - " + mqttMessage.toString());
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken)
                {
                    addMessage("Publisher: deliveryComplete.");
                    try
                    {
                        pubClient.disconnect();
                    }
                    catch(MqttException e)
                    {
                        e.printStackTrace();
                    }
                }
            });

            pubClient.connect(connOpts, null, new IMqttActionListener()
            {
                @Override
                public void onSuccess(IMqttToken iMqttToken)
                {
                    v.setEnabled(false);

                    addMessage("Publisher: onSuccess.");

                    try
                    {
                        pubClient.publish(topic, message);
                    }
                    catch(MqttException e)
                    {
                        addMessage("Publisher connect exception: " + e.getMessage());
                        addMessage("cause " + e.getCause());
                        addMessage("excep " + e);
                    }
                }

                @Override
                public void onFailure(IMqttToken iMqttToken, Throwable throwable)
                {
                    addMessage("Publisher: onFailure: " + throwable.getMessage());
                    addMessage("cause " + throwable.getCause());
                    addMessage("excep " + throwable);
                }
            });
        }
        catch(MqttException e)
        {
            addMessage("Publisher exception: " + e.getMessage());
            addMessage("cause " + e.getCause());
            addMessage("excep " + e);
        }
    }

    public static final String KEY_SUB_HOST = "SUB_HOST";
    public static final String KEY_SUB_PORT = "SUB_PORT";
    public static final String KEY_PUB_HOST = "PUB_HOST";
    public static final String KEY_PUB_PORT = "PUB_PORT";

    public static final String KEY_SUB_ID = "SUB_ID";
    public static final String KEY_PUB_ID = "PUB_ID";

    public static final String KEY_SUB_TOPIC = "SUB_TOPIC";
    public static final String KEY_PUB_TOPIC = "PUB_TOPIC";
    public static final String KEY_PUB_PAYLOAD = "PUB_PAYLOAD";

    public static final String KEY_SUB_USERNAME = "SUB_USERNAME";
    public static final String KEY_SUB_PASSWORD = "SUB_PASSWORD";
    public static final String KEY_PUB_USERNAME = "PUB_USERNAME";
    public static final String KEY_PUB_PASSWORD = "PUB_PASSWORD";
    
    public static final String KEY_SUB_QOS = "SUB_QOS";
    public static final String KEY_PUB_QOS = "PUB_QOS";
    
    protected void saveSettings()
    {
        SharedPreferences sp = getPreferences(MODE_PRIVATE);

        sp.edit().putString(KEY_SUB_HOST, brokerSubHost.getText().toString())
                 .putString(KEY_SUB_PORT, brokerSubPort.getText().toString())
                 .putString(KEY_PUB_HOST, brokerPubHost.getText().toString())
                 .putString(KEY_PUB_PORT, brokerPubPort.getText().toString())
                 .putString(KEY_SUB_ID, subId.getText().toString())
                 .putString(KEY_PUB_ID, pubId.getText().toString())
                 .putString(KEY_SUB_TOPIC, subTopic.getText().toString())
                 .putString(KEY_PUB_TOPIC, pubTopic.getText().toString())
                 .putString(KEY_PUB_PAYLOAD, pubPayload.getText().toString())
                 .putInt(KEY_SUB_QOS, spnQosSub.getSelectedItemPosition())
                 .putInt(KEY_PUB_QOS, spnQosPub.getSelectedItemPosition())
                 .apply();
    }
    
    protected void loadSettings()
    {
        SharedPreferences sp = getPreferences(MODE_PRIVATE);
        
        brokerSubHost.setText(sp.getString(KEY_SUB_HOST, "tcp://"));
        brokerSubPort.setText(sp.getString(KEY_SUB_PORT, "1883"));
        brokerPubHost.setText(sp.getString(KEY_PUB_HOST, "tcp://"));
        brokerPubPort.setText(sp.getString(KEY_PUB_PORT, "1883"));
        subId.setText(sp.getString(KEY_SUB_ID, "Aaron"));
        pubId.setText(sp.getString(KEY_PUB_ID, "Aaron"));
        subTopic.setText(sp.getString(KEY_SUB_TOPIC, "MQTT1"));
        pubTopic.setText(sp.getString(KEY_PUB_TOPIC, "MQTT1"));
        pubPayload.setText(sp.getString(KEY_PUB_PAYLOAD, "Hello MQTT!!!"));
        spnQosSub.setSelection(sp.getInt(KEY_SUB_QOS, 2));
        spnQosPub.setSelection(sp.getInt(KEY_PUB_QOS, 2));
    }

    protected void addMessage(final String msg)
    {
        Log.d(LOGTAG, msg);

        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                String all = txtMessage.getText().toString();

                all += "\n" + msg;

                if(all.length() > 15000)
                    all = all.substring(all.length() - 15000);

                txtMessage.setText(all);

                scrollView.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        scrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    protected void showNotification(String topic, String msg)
    {
        final int NOTI_ID = 0;

        // 建立Notification
        NotificationCompat.Builder b = new NotificationCompat.Builder(this);

        b.setSmallIcon(android.R.drawable.ic_dialog_alert);     // 設定Icon
        b.setTicker(msg);           // 設定系統列訊息
        b.setContentTitle(topic); // 設定標題
        b.setContentText(msg);      // 訊息
        b.setWhen(System.currentTimeMillis()); // 立即顯示
        b.setContentInfo(msg);              // 設定內容
        b.setDefaults(Notification.DEFAULT_SOUND |
                      Notification.DEFAULT_VIBRATE |
                      Notification.DEFAULT_LIGHTS);

        // 在Notification Drawer點下去後要呼叫的Activity
//        String urlString="http://www.aaronlife.com";
//        Intent intent=new Intent(Intent.ACTION_VIEW, Uri.parse(urlString));
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        intent.setPackage("com.android.chrome");
//
//        PendingIntent pit = PendingIntent.getActivity(
//                this, // 目前所在的Activity
//                0, // request code, 這裡不重要
//                intent, // 想要備觸發的Intent
//                PendingIntent.FLAG_UPDATE_CURRENT);
//
//        b.setContentIntent(pit);    // 設定在系統頁被點擊時要觸發的Intent

        Notification n = b.build();
        // n.flags |= Notification.FLAG_NO_CLEAR; // 不允許清除

        // 下面兩行如果是在Activity裡面要丟訊息到系統列須使用NotificationManager
        NotificationManager nm =
            (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTI_ID, n);

        //nm.cancel(NOTI_ID);
    }
}