package com.example.ericpeng.drawertest;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

import android.support.v7.widget.PopupMenu;
import android.util.Log;
import java.util.Date;
import java.text.*;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;

import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.UploadErrorException;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.users.FullAccount;
import com.squareup.picasso.Picasso;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Set;

import static android.content.ContentValues.TAG;

public class RecordActivity extends Activity implements OnClickListener, PopupMenu.OnMenuItemClickListener {

    private BluetoothAdapter BA;
    private Set<BluetoothDevice>pairedDevices;
    public ArrayList<String> activities;
    ListView lv;
    TextView data;
    TextView history;

    Handler bluetoothIn;

    boolean clicked = false;
    boolean firstTime = true;

    public volatile boolean stopThread = false;

    final int handlerState = 0;
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder recDataString = new StringBuilder();

    private ConnectedThread mConnectedThread;
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static String address;

    String currentText, historyText;

    private String file = "usageData";

    //private String ACCESS_TOKEN = "gGam-m6a5gAAAAAAAAAAoZcookFEX9U1eUIqh2212jvKPw6CZxci8CYlXiPT8iEe";

    private static final int PICKFILE_REQUEST_CODE = 1;
    private String ACCESS_TOKEN;

    private String mPath;
    private DropboxMainActivity mFilesAdapter;
    private FileMetadata mSelectedFile;

    Button start;
    Button stop;
    Button save;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);

        start = (Button)findViewById(R.id.start_button);
        stop = (Button)findViewById(R.id.stop_button);
        save = (Button)findViewById(R.id.save_button);

        start.setOnClickListener(this);
        stop.setOnClickListener(this);

        BA = BluetoothAdapter.getDefaultAdapter();

        activities = new ArrayList<>();

        //data = (TextView)findViewById(R.id.data);
        history = (TextView)findViewById(R.id.history);

        //data.setVisibility(View.INVISIBLE);
        history.setVisibility(View.INVISIBLE);

        start.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                stopThread = false;
                writeMessage();
            }
        });
        //writeMessage();
        bluetoothIn = new Handler() {
            public void handleMessage(android.os.Message msg) {
                //data.setText("");
                if (msg.what == handlerState) {
                    String readMessage = (String) msg.obj;

                    recDataString.append(readMessage);
                    int value = recDataString.toString().lastIndexOf('.');
                    String voltage = recDataString.substring(value - 1, recDataString.toString().length()-1);

                    voltage = voltage.trim().replaceAll("\n ", "");
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy | HH:mm:ss");
                    String currentTime  = dateFormat.format(new Date());

                    if (!stopThread) {
                        currentText = "Voltage = " + voltage + " V";
                        historyText = currentTime + " (" + voltage + " V)\n" + history.getText().toString();
                    }
                }
                //data.setMovementMethod(new ScrollingMovementMethod());
                history.setMovementMethod(new ScrollingMovementMethod());

                //data.setText(currentText);
                history.setText(historyText);
            }
        };

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();

        stop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                stopThread = true;
                firstTime = false;
            }
        });

        save.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popup = new PopupMenu(RecordActivity.this, save);
                //popup.getMenuInflater().inflate(R.menu.popup, popup.getMenu());
                popup.setOnMenuItemClickListener(RecordActivity.this);
                popup.inflate(R.menu.popup);
                popup.show();

            }
        });

        if (!tokenExists()) {
            //No token
            //Back to LoginActivity
            Intent intent = new Intent(RecordActivity.this, DropboxLoginActivity.class);
            startActivity(intent);
        }

        ACCESS_TOKEN = retrieveAccessToken();
        getUserAccount();
    }

    protected void getUserAccount() {
        if (ACCESS_TOKEN == null)return;
        new UserAccountTask(DropboxClient.getClient(ACCESS_TOKEN), new UserAccountTask.TaskDelegate() {
            @Override
            public void onAccountReceived(FullAccount account) {
                //Print account's info
                Log.d("User", account.getEmail());
                Log.d("User", account.getName().getDisplayName());
                Log.d("User", account.getAccountType().name());
                //updateUI(account);
            }
            @Override
            public void onError(Exception error) {
                Log.d("User", "Error receiving account details.");
            }
        }).execute();
    }

    private void updateUI(FullAccount account) {
        ImageView profile = (ImageView) findViewById(R.id.imageView);
        TextView name = (TextView) findViewById(R.id.name_textView);
        TextView email = (TextView) findViewById(R.id.email_textView);

        name.setText(account.getName().getDisplayName());
        email.setText(account.getEmail());
        Picasso.with(this)
                .load(account.getProfilePhotoUrl())
                .resize(200, 200)
                .into(profile);
    }

    private void upload(String text) {
        if (ACCESS_TOKEN == null)return;

        SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH:mm:ss");
        String currentTime  = dateFormat.format(new Date());
        // Launch intent to pick file for upload
        System.out.println(text);

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        File myDir = getFilesDir();


        try {
            new File("/data/user/0/com.example.ericpeng.drawertest/files/text/").mkdir();
            File file = new File("/data/user/0/com.example.ericpeng.drawertest/files/text/"+ "data-" + currentTime + ".txt");
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fileOutputStream = new FileOutputStream(file,true);
            fileOutputStream.write((text + System.getProperty("line.separator")).getBytes());

            if (file != null) {
                //Initialize UploadTask
                new UploadTask(DropboxClient.getClient(ACCESS_TOKEN), file, getApplicationContext()).execute();
            }

        }  catch(FileNotFoundException ex) {
            Log.d(TAG, ex.getMessage());
        }  catch(IOException ex) {
            Log.d(TAG, ex.getMessage());
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        // Check which request we're responding to
        if (requestCode == PICKFILE_REQUEST_CODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {

                File myDir = getFilesDir();
                File file = new File(myDir + "/text/" + "test");
                if (file.getParentFile().mkdirs()){
                    try {
                        file.createNewFile();
                        FileOutputStream fos = new FileOutputStream(file);
                        fos.flush();
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (file != null) {
                    //Initialize UploadTask
                    new UploadTask(DropboxClient.getClient(ACCESS_TOKEN), file, getApplicationContext()).execute();
                }
            }
        }
    }

    private boolean tokenExists() {
        SharedPreferences prefs = getSharedPreferences("com.example.valdio.dropboxintegration", Context.MODE_PRIVATE);
        String accessToken = prefs.getString("access-token", null);
        return accessToken != null;
    }

    private String retrieveAccessToken() {
        //check if ACCESS_TOKEN is stored on previous app launches
        SharedPreferences prefs = getSharedPreferences("com.example.valdio.dropboxintegration", Context.MODE_PRIVATE);
        String accessToken = prefs.getString("access-token", null);
        if (accessToken == null) {
            Log.d("AccessToken Status", "No token found");
            return null;
        } else {
            //accessToken already exists
            Log.d("AccessToken Status", "Token exists");
            return accessToken;
        }
    }

    public boolean onMenuItemClick(MenuItem item){
        String text = history.getText().toString();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH:mm:ss");
        String currentTime  = dateFormat.format(new Date());
        GlobalVars g = new GlobalVars();
        switch (item.getItemId()){
            case R.id.email_save:
                Intent email_intent = new Intent(Intent.ACTION_SEND);
                email_intent.setType("*/*");
                email_intent.putExtra(android.content.Intent.EXTRA_TEXT, text);
                //activities.add(currentTime +  ": Sent as Email");
                g.setGlobalVar(currentTime +  ": Sent as Email");
                startActivity(email_intent);
                return true;

            case R.id.dropbox_save:
                File currentFile = new File("Data-" + dateFormat);

                upload(text);

                return true;

            case R.id.delete_data:
                //Toast.makeText(this, "Delete Clicked", Toast.LENGTH_SHORT).show();
                return true;

            case R.id.yes_delete:
                //data.setText("");
                //history.setText("");
                firstTime = true;
                finish();
                startActivity(getIntent());
                //Toast.makeText(this, "Yes Clicked", Toast.LENGTH_SHORT).show();
                return true;

            case R.id.no_delete:
                //Toast.makeText(this, "No Clicked", Toast.LENGTH_SHORT).show();
                return true;
        }
        try {
            FileOutputStream fOut = openFileOutput(file,MODE_WORLD_READABLE);
            //fOut.write(data.getBytes());
            fOut.close();
            Toast.makeText(getBaseContext(),"file saved",Toast.LENGTH_SHORT).show();
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        //System.out.println(activities);
        //Toast.makeText(RecordActivity.this,"You Clicked : " + item.getTitle(),Toast.LENGTH_SHORT).show();
        return true;
    }


    public void writeMessage(){
        if (firstTime){
            //data.setText("");
            history.setText("");
        }
        //data.setVisibility(View.VISIBLE);
        history.setVisibility(View.VISIBLE);

    }

    public void onClick(View view){
        if(view.equals(save)){
        }
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    @Override
    public void onResume() {
        super.onResume();

        Intent intent = getIntent();

        address = "98:D3:37:00:98:0B";

        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e) {
            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_LONG).show();
        }
        // Establish the Bluetooth socket connection.
        try {
            btSocket.connect();
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                Toast.makeText(getBaseContext(), "Connection Closed", Toast.LENGTH_LONG).show();

            }
        }
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();
        //mConnectedThread.write("x");
    }

    @Override
    public void onPause()
    {
        super.onPause();
        mConnectedThread.cancel();
        try{
            btSocket.close();
        } catch (IOException e2) {
        }
    }

    private void checkBTState() {

        if(btAdapter==null) {
            Toast.makeText(getBaseContext(), "Device does not support bluetooth", Toast.LENGTH_LONG).show();
        } else {
            if (btAdapter.isEnabled()) {
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }


    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        //creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }
            System.out.println("INTERRUPTED Click");
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void cancel() {

        }

        public void run() {
            byte[] buffer = new byte[256];
            int bytes;

            while (!interrupted()) {
                try {
                    bytes = mmInStream.read(buffer);            //read bytes from input buffer
                    String readMessage = new String(buffer, 0, bytes);
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }

            }
        }

        public void write(String input) {
            byte[] msgBuffer = input.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {
                Toast.makeText(getBaseContext(), "Connection Failure", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    public void on(View v){
        if (!BA.isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnOn, 0);
            Toast.makeText(getApplicationContext(), "Turned on",Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), "Already on", Toast.LENGTH_LONG).show();
        }
    }

    public void off(View v){
        BA.disable();
        Toast.makeText(getApplicationContext(), "Turned off" ,Toast.LENGTH_LONG).show();
    }


    public void visible(View v){
        Intent getVisible = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        startActivityForResult(getVisible, 0);
    }


    public void list(View v){
        pairedDevices = BA.getBondedDevices();

        ArrayList list = new ArrayList();

        for(BluetoothDevice bt : pairedDevices) list.add(bt.getName());
        Toast.makeText(getApplicationContext(), "Showing Paired Devices",Toast.LENGTH_SHORT).show();

        final ArrayAdapter adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, list);

        lv.setAdapter(adapter);
    }
}
