package com.arnavdhamija.nearbysimple;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
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

import java.io.File;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MainActivity extends AppCompatActivity {
    private final String codeName = CodenameGenerator.generate();
    private ConnectionsClient mConnectionClient;
    private String connectedEndpoint;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CHANGE_WIFI_STATE);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    0);
        }

        Button button = (Button) findViewById(R.id.mybutton);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("app", "choosing img");
                showImageChooser(connectedEndpoint);
            }
        });
        mConnectionClient = Nearby.getConnectionsClient(this);
        Log.d("app", "wofo: " + permissionCheck);
        startAdvertising();
        startDiscovery();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode) {
            case 0: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("app", "Got coarse Loc :)");
                } else {
                    Log.d("app", "oh well");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;

            }
        }
    }

    private void sendWelcomeMessage() {
        String welcome = "welcome2beconnected from " + codeName;
        mConnectionClient.sendPayload(connectedEndpoint, Payload.fromBytes(welcome.getBytes(UTF_8)));
    }

    private static final int READ_REQUEST_CODE = 42;

    private void showImageChooser(String endpointId) {
        Log.d("app", "endpnt id" + endpointId);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra("endpointId", endpointId);
        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_REQUEST_CODE && resultCode == this.RESULT_OK) {
            if (resultData != null) {
                Log.d("app", "Got a data");
                String endpointId = resultData.getStringExtra("endpointId");

                // The URI of the file selected by the user.
                Uri uri = resultData.getData();

                // Open the ParcelFileDescriptor for this URI with read access.
                try {
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                    Payload filePayload = Payload.fromFile(pfd);

                    // Construct a simple message mapping the ID of the file payload to the desired filename.
                    String payloadFilenameMessage = filePayload.getId() + ":" + uri.getLastPathSegment();

                    // Send this message as a bytes payload.
                    mConnectionClient.sendPayload(
                            endpointId, Payload.fromBytes(payloadFilenameMessage.getBytes("UTF-8")));

                    // Finally, send the file payload.
                    mConnectionClient.sendPayload(endpointId, filePayload);
                } catch (Exception e) {

                }
            }
        }
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
                                Log.d("app", "Advertising Go!");
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // We were unable to start advertising.
                            }
                        });
    }

    private final PayloadCallback mPayloadCallback =
            new PayloadCallback() {
                private final SimpleArrayMap<Long, Payload> incomingPayloads = new SimpleArrayMap<>();
                private final SimpleArrayMap<Long, String> filePayloadFilenames = new SimpleArrayMap<>();

                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    if (payload.getType() == Payload.Type.BYTES) {
                        try {
                            Log.d("app", "Getting a byte pyalod");
                            String payloadFilenameMessage = new String(payload.asBytes(), "UTF-8");
                            addPayloadFilename(payloadFilenameMessage);
                        } catch (Exception e) {

                        }
                    } else if (payload.getType() == Payload.Type.FILE) {
                        Log.d("app", "Getting a file pyalod");
                        // Add this to our tracking map, so that we can retrieve the payload later.
                        incomingPayloads.put(new Long(payload.getId()), payload);
                    }
                }

                /**
                 * Extracts the payloadId and filename from the message and stores it in the
                 * filePayloadFilenames map. The format is payloadId:filename.
                 */
                private void addPayloadFilename(String payloadFilenameMessage) {
                    int colonIndex = payloadFilenameMessage.indexOf(':');
                    String payloadId = payloadFilenameMessage.substring(0, colonIndex);
                    String filename = payloadFilenameMessage.substring(colonIndex + 1);
                    filePayloadFilenames.put(Long.parseLong(payloadId), filename);
                }

                @Override
                public void onPayloadTransferUpdate(String payloadId, PayloadTransferUpdate update) {
                    if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                        Payload payload = incomingPayloads.remove(payloadId);
                        if (payload.getType() == Payload.Type.FILE) {
                            // Retrieve the filename that was received in a bytes payload.
                            String newFilename = filePayloadFilenames.remove(payloadId);

                            File payloadFile = payload.asFile().asJavaFile();

                            // Rename the file.
                            payloadFile.renameTo(new File(payloadFile.getParentFile(), newFilename));
                        }
                    }
                }

            };


    private final ConnectionLifecycleCallback mConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {

                @Override
                public void onConnectionInitiated(
                        String endpointId, ConnectionInfo connectionInfo) {
                    // Automatically accept the connection on both sides.
                    mConnectionClient.acceptConnection(endpointId, mPayloadCallback);
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            Log.d("app", "GGWP! :D:D:D");
                            Toast.makeText(getApplicationContext(), "Connection Established", Toast.LENGTH_LONG).show();
                            TextView textView = (TextView) findViewById(R.id.textbox0);
                            textView.setText("Connection Established with " + endpointId);
                            connectedEndpoint = endpointId;
                            sendWelcomeMessage();
                            mConnectionClient.stopAdvertising();
                            mConnectionClient.stopDiscovery();
                            // We're connected! Can now start sending and receiving data.
                            break;
                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                            // The connection was rejected by one or both sides.
                            Log.d("app", "fail D:");
                            break;
                        case ConnectionsStatusCodes.STATUS_ERROR:
                            Log.d("app", "bigfail");
                            // The connection broke before it was able to be accepted.
                            break;
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    // We've been disconnected from this endpoint. No more data can be
                    // sent or received.
                    Log.d("app", "connection terminated, find a way to autostart");
                    startAdvertising();
                    startDiscovery();
                }
            };


    private final EndpointDiscoveryCallback mEndpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(
                        String endpointId, DiscoveredEndpointInfo discoveredEndpointInfo) {
                    Log.d("app", "FOUND ENDPOINT: " + endpointId + "Info " + discoveredEndpointInfo.getEndpointName() + " id " + discoveredEndpointInfo.getServiceId());
                    mConnectionClient.requestConnection(
                            codeName,
                            endpointId,
                            mConnectionLifecycleCallback).addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d("app", "requesting conn");
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d("app", "fail conn t_t" + e.getMessage());
                        }
                    });
                }

                @Override
                public void onEndpointLost(String endpointId) {
                    Log.d("app", "lost ENDPOINT: " + endpointId);

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
                                Log.d("app", "Discovery go!");
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
