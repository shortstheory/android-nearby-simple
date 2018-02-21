package com.arnavdhamija.nearbysimple;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate.Status;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MainActivity extends AppCompatActivity {
    private final String codeName = CodenameGenerator.generate();
    private ConnectionsClient mConnectionClient;
    private String connectedEndpoint;

    private void mylogger(String tag, String msg) {
        Log.d(tag, msg);
        TextView logView = findViewById(R.id.logView);
        String timeStamp = new SimpleDateFormat("HH.mm.ss").format(new Date());
        logView.append(timeStamp+' '+msg+"\n");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CHANGE_WIFI_STATE);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    0);
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    0);
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    0);
        }
        final String TAG = "app";
        mConnectionClient = Nearby.getConnectionsClient(this);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mylogger("app", "wofo: " + permissionCheck);
        startAdvertising();
        startDiscovery();

        Button button = (Button) findViewById(R.id.mybutton);
        Button sendMsg = findViewById(R.id.send_msg);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mylogger("app", "choosing img");
                showImageChooser(connectedEndpoint);
            }
        });
        sendMsg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mylogger(TAG, "pressed button");
                sendWelcomeMessage();
            }
        });
//
        Button updateButton = findViewById(R.id.updateButton);
        updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView logView = findViewById(R.id.logView);
                logView.setText("");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode) {
            case 0: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mylogger("app", "Got coarse Loc :)");
                } else {
                    mylogger("app", "oh well");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;

            }
        }
    }

    private void sendWelcomeMessage() {
        String welcome = "welcome2beconnected from " + codeName + " to " + connectedEndpoint;
        mConnectionClient.sendPayload(connectedEndpoint, Payload.fromBytes(welcome.getBytes(UTF_8)));
    }

    final static int PICK_IMAGE = 1; // required for getting the result from the image picker intent

    private void showImageChooser(String endpointId) {
        mylogger("app", "endpnt id" + endpointId);
        Intent intent = new Intent();

        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.putExtra("endpointId", endpointId);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == PICK_IMAGE && resultCode == this.RESULT_OK) {
            if (resultData != null) {
                mylogger("app", "Got a data");
                String endpointId = resultData.getStringExtra("endpointId");

                // The URI of the file selected by the user.
                Uri uri = resultData.getData();
                mylogger("app", "chosen uri" + uri.toString());

                // Open the ParcelFileDescriptor for this URI with read access.
                try {
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                    Payload filePayload = Payload.fromFile(pfd);

                    // Construct a simple message mapping the ID of the file payload to the desired filename.
                    String payloadFilenameMessage = filePayload.getId() + ":" + getFileName(uri);

                    // Send this message as a bytes payload.
                    Payload.File file = filePayload.asFile();
                    // Finally, send the file payload.
                    sendPayload(connectedEndpoint, filePayload, payloadFilenameMessage);
                    mylogger("app", "successful send to " + connectedEndpoint + " size " + file.getSize() + " k/v " + payloadFilenameMessage);
                } catch (Exception e) {
                    mylogger("app", "failsend" + e.getMessage());
                }
            }
        }
    }
    private final SimpleArrayMap<Long, NotificationCompat.Builder> incomingPayloads = new SimpleArrayMap<>();
    private final SimpleArrayMap<Long, NotificationCompat.Builder> outgoingPayloads = new SimpleArrayMap<>();
    private final SimpleArrayMap<Long, Payload> incomingPayloadReferences = new SimpleArrayMap<>();
    private final SimpleArrayMap<Long, String> filePayloadFilenames = new SimpleArrayMap<>();

    /**
     * Extracts the payloadId and filename from the message and stores it in the
     * filePayloadFilenames map. The format is payloadId:filename.
     */
    private void addPayloadFilename(String payloadFilenameMessage) {
        int colonIndex = payloadFilenameMessage.indexOf(':');
        String payloadId = payloadFilenameMessage.substring(0, colonIndex);
        String filename = payloadFilenameMessage.substring(colonIndex + 1);
        filePayloadFilenames.put(Long.valueOf(payloadId), filename);
    }


    NotificationManager mNotificationManager;
    int notifId = 0;
    private void sendPayload(String endpointId, Payload payload, String payloadFilenameMsg) {
        if (payload.getType() == Payload.Type.BYTES) {
            return;
        }
        NotificationCompat.Builder notification = buildNotification(payload, false);
        mNotificationManager.notify((int)payload.getId(), notification.build());
        outgoingPayloads.put(Long.valueOf(payload.getId()), notification);
        try {
            mConnectionClient.sendPayload(endpointId, Payload.fromBytes(payloadFilenameMsg.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            mylogger("app", "encode fail");
        }
        mConnectionClient.sendPayload(endpointId, payload);
    }

    private void startAdvertising() {
        mConnectionClient.startAdvertising(
                codeName,
                getPackageName(),
                mConnectionLifecycleCallback,
                new AdvertisingOptions(Strategy.P2P_CLUSTER))
                .addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unusedResult) {
                                // We're advertising!
                                mylogger("app", "Advertising Go!");
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                mylogger("app", "advert fail");
                                // We were unable to start advertising.
                            }
                        });
    }

    private NotificationCompat.Builder buildNotification(Payload payload, boolean isIncoming) {
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this).setContentTitle(isIncoming ? "Receiving..." : "Sending...").setSmallIcon(R.drawable.common_full_open_on_phone);
        long size = payload.asFile().getSize();
        boolean indeterminate = false;
        if (size == -1) {
            // This is a stream payload, so we don't know the size ahead of time.
            size = 100;
            indeterminate = true;
        }
        notification.setProgress((int)size, 0, indeterminate);
        return notification;
    }


    private final PayloadCallback mPayloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    mylogger("app", "getting a payload");
                    if (payload.getType() == Payload.Type.BYTES) {
                        try {
                            String payloadFilenameMessage = new String(payload.asBytes(), "UTF-8");
                            addPayloadFilename(payloadFilenameMessage);
                            mylogger("app", "Getting a byte pyalod" + payloadFilenameMessage);
//                            addPayloadFilename(payloadFilenameMessage);
                        } catch (Exception e) {

                        }
                    } else if (payload.getType() == Payload.Type.FILE) {
                        mylogger("app", "Getting a file pyalod " + payload.asFile().getSize());
                        NotificationCompat.Builder notification = buildNotification(payload, true /*isIncoming*/);
                        mNotificationManager.notify((int) payload.getId(), notification.build());
                        incomingPayloads.put(Long.valueOf(payload.getId()), notification);
                        incomingPayloadReferences.put(payload.getId(), payload);
                    }
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    NotificationCompat.Builder notification = new NotificationCompat.Builder(getApplicationContext()).setSmallIcon(R.drawable.common_full_open_on_phone);
                    long payloadId = update.getPayloadId();
                    if (incomingPayloads.containsKey(payloadId)) {
                        notification = incomingPayloads.get(payloadId);
                        if (update.getStatus() != PayloadTransferUpdate.Status.IN_PROGRESS) {
                            // This is the last update, so we no longer need to keep track of this notification.
                            incomingPayloads.remove(payloadId);
                        }
                    } else if (outgoingPayloads.containsKey(payloadId)) {
                        notification = outgoingPayloads.get(payloadId);
                        if (update.getStatus() != PayloadTransferUpdate.Status.IN_PROGRESS) {
                            // This is the last update, so we no longer need to keep track of this notification.
                            outgoingPayloads.remove(payloadId);
                        }
                    }

                    switch(update.getStatus()) {
                        case Status.IN_PROGRESS:
                            int size = (int)update.getTotalBytes();
//                            mylogger("app", "Bytes transferred " + update.getBytesTransferred());
                            if (size == -1) {
                                // This is a stream payload, so we don't need to update anything at this point.
                                return;
                            }
                            notification.setProgress(size, (int)update.getBytesTransferred(), false /* indeterminate */);
                            break;
                        case Status.SUCCESS:
                            // SUCCESS always means that we transferred 100%.
                            notification
                                    .setProgress(100, 100, false /* indeterminate */)
                                    .setContentText("Transfer complete!");
                            mylogger("app", "Transfer done");
                            Payload payload = incomingPayloadReferences.remove(update.getPayloadId());
                            String filename = filePayloadFilenames.remove(update.getPayloadId());
                            if (payload != null) {
                                File payloadFile = payload.asFile().asJavaFile();
                                payloadFile.renameTo(new File(payloadFile.getParentFile(), filename));
                                mylogger("app", "found N renamed");
                            } else {
                                mylogger("app", "NUll");
                            }
                            break;
                        case Status.FAILURE:
                            notification
                                    .setProgress(0, 0, false)
                                    .setContentText("Transfer failed");
                            break;
                    }

                    mNotificationManager.notify((int)payloadId, notification.build());
                }

            };


    private final ConnectionLifecycleCallback mConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {

                @Override
                public void onConnectionInitiated(
                        String endpointId, ConnectionInfo connectionInfo) {
                    // Automatically accept the connection on both sides.
                    mylogger("app", "Connection initated");
                    mConnectionClient.acceptConnection(endpointId, mPayloadCallback);
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            mylogger("app", "GGWP! :D:D:D");
                            Toast.makeText(getApplicationContext(), "Connection Established", Toast.LENGTH_LONG).show();
                            TextView textView = (TextView) findViewById(R.id.textbox0);
                            textView.setText("Connection Established with " + endpointId);
                            connectedEndpoint = endpointId;
                            sendWelcomeMessage();
                            mylogger("app", "stopping AD");
                            mConnectionClient.stopAdvertising();
                            mConnectionClient.stopDiscovery();
                            mylogger("app", "stopped AD!!");
                            // We're connected! Can now start sending and receiving data.
                            break;
                        case ConnectionsStatusCodes.STATUS_ENDPOINT_IO_ERROR: //this code is ignored
                            mylogger("app", "endpt error, restart");
                            restartNearby();
                            break;
                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                            // The connection was rejected by one or both sides.
                            mylogger("app", "fail D:");
                            break;
                        case ConnectionsStatusCodes.STATUS_ERROR:
                            mylogger("app", "bigfail");
                            // The connection broke before it was able to be accepted.
                            break;
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    // We've been disconnected from this endpoint. No more data can be
                    // sent or received.
                    mylogger("app", "connection terminated, find a way to autostart");
                    TextView textView = (TextView) findViewById(R.id.textbox0);
                    textView.setText("disconnected from " + endpointId);
                    restartNearby();
                }
            };

    private void restartNearby() {
        mylogger("app", "RestartingNearby");
        mConnectionClient.stopAdvertising();
        mConnectionClient.stopDiscovery();
        startAdvertising();
        startDiscovery();
    }

    private final static int MAX_TRIES = 3;
    private final EndpointDiscoveryCallback mEndpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(
                        String endpointId, DiscoveredEndpointInfo discoveredEndpointInfo) {
                    mylogger("app", "FOUND ENDPOINT: " + endpointId + "Info " + discoveredEndpointInfo.getEndpointName() + " id " + discoveredEndpointInfo.getServiceId());
                    mConnectionClient.stopAdvertising();
                    mConnectionClient.stopDiscovery();
                    mylogger("app", "Stopping before requesting Conn");
                    mConnectionClient.requestConnection(
                            codeName,
                            endpointId,
                            mConnectionLifecycleCallback).addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            mylogger("app", "requesting conn");
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            mylogger("app", "fail conn t_t" + e.getMessage());
//                            if (e.getMessage().compareTo("STATUS_ENDPOINT_IO_ERROR")==0) {
//                                mylogger("app", "restarting to try again");

//                            }
                        }
                    });
                }

                @Override
                public void onEndpointLost(String endpointId) {
                    mylogger("app", "lost ENDPOINT: " + endpointId);

                    // A previously discovered endpoint has gone away.
                }
            };

    private void startDiscovery() {
        mConnectionClient.startDiscovery(
                getPackageName(),
                mEndpointDiscoveryCallback,
                new DiscoveryOptions(Strategy.P2P_CLUSTER))
                .addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unusedResult) {
                                mylogger("app", "Discovery go!");
                                // We're discovering!
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // We were unable to start discovering.
                            }
                        });
    }


}
