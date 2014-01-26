/*
 * Copyright (C) 2013 Keisuke SUZUKI
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * This code is checked by Galaxy S II and FT232RL
 */
package com.ksksue.app.ftdi_uart;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;
//import com.ksksue.app.fpga_fifo.R;
import com.ksksue.app.fpga_fifo.R;

public class MainActivity extends Activity {
    private final static String TAG = "FPGA_FIFO Activity";

    private static D2xxManager ftD2xx = null;
    private FT_Device ftDev;

    static final int READBUF_SIZE  = 256;
    byte[] rbuf  = new byte[READBUF_SIZE];
    char[] rchar = new char[READBUF_SIZE];
    int mReadSize=0;

    TextView tvRead;
    ScrollView tvReadScroll;
    EditText etWrite;
    Button btOpen;
    Button btWrite;
    Button btClose;

    boolean mThreadIsStopped = true;
    Handler mHandler = new Handler();
    Thread mThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvRead = (TextView) findViewById(R.id.tvReadText);
        etWrite = (EditText) findViewById(R.id.etWrite);

        btOpen = (Button) findViewById(R.id.btOpen);
        btWrite = (Button) findViewById(R.id.btWrite);
        btClose = (Button) findViewById(R.id.btClose);
        
        tvReadScroll = (ScrollView) findViewById(R.id.tvRead);
//        tvReadScroll.fullScroll(View.FOCUS_DOWN);

        updateView(false);

        try {
            ftD2xx = D2xxManager.getInstance(this);
        } catch (D2xxManager.D2xxException ex) {
            Log.e(TAG,ex.toString());
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

//      String text = "<font color=#cc0029>OnCreate finished en rojo</font>";
        String text = "<font color=#ffcc00>OnCreate finished</font>";
        tvRead.append("\n");
        tvRead.append(Html.fromHtml(text));
        tvRead.append("\n");

    }

    public void onClickOpen(View v) {
        openDevice();
    }


    public void onClickWrite(View v) {
        String text = "<font color=#ffcc00>OnClickWrite starting</font>";
        tvRead.append("\n");
        tvRead.append(Html.fromHtml(text));
        tvRead.append("\n");

        if(ftDev == null) {
            return;
        }
        synchronized (ftDev) {
            if(ftDev.isOpen() == false) {
                Log.e(TAG, "onClickWrite : Device is not open");
                return;
            }
            ftDev.setLatencyTimer((byte)16);

            String writeString = etWrite.getText().toString();
            writeString+="\n"; // add the end of the line
            byte[] writeByte = writeString.getBytes();
            ftDev.write(writeByte, writeString.length());
            // clean the content of the EditText
            etWrite.setText("");
        }
        
        text = "<font color=#ffcc00>OnClickWrite finished</font>";
        tvRead.append("\n");
        tvRead.append(Html.fromHtml(text));
        tvRead.append("\n");
    }

    public void onClickClose(View v) {
        closeDevice();
    }

    public void onClickClear(View v) {
        tvRead.setText("");
    }    
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        mThreadIsStopped = true;
        unregisterReceiver(mUsbReceiver);
    }

/*    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
*/

    private void openDevice() {
//    	Log.d(TAG, "Open Device");
        String text = "<font color=#cc0029>openDevice starting</font>";
        tvRead.append("\n");
        tvRead.append(Html.fromHtml(text));
        tvRead.append("\n");
    	
        if(ftDev != null) {
            if(ftDev.isOpen()) {
                if(mThreadIsStopped) {
                    updateView(true);
                    SetConfig(115200, (byte)8, (byte)1, (byte)0, (byte)0);
                    ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
                    ftDev.restartInTask();
                    new Thread(mLoop).start();
                }
                return;
            }
        }

        int devCount = 0;
        devCount = ftD2xx.createDeviceInfoList(this);

        Log.d(TAG, "Device number : "+ Integer.toString(devCount));
        text = "<font color=#cc0029>Device number </font>" + Integer.toString(devCount);
        tvRead.append("\n");
        tvRead.append(Html.fromHtml(text));
        tvRead.append("\n");


        D2xxManager.FtDeviceInfoListNode[] deviceList = new D2xxManager.FtDeviceInfoListNode[devCount];
        ftD2xx.getDeviceInfoList(devCount, deviceList);

        if(devCount <= 0) {
            return;
        }

        if(ftDev == null) {
            ftDev = ftD2xx.openByIndex(this, 1);
            text = "<font color=#cc0029>ftDev == null</font>";
            tvRead.append("\n");
            tvRead.append(Html.fromHtml(text));
            tvRead.append("\n");                        
        } else {
            text = "<font color=#cc0029>Before synchronized</font>";
            tvRead.append("\n");
            tvRead.append(Html.fromHtml(text));
            tvRead.append("\n");                                	
            synchronized (ftDev) {
                ftDev = ftD2xx.openByIndex(this, 1);
            }
            text = "<font color=#cc0029>After synchronized</font>";
            tvRead.append("\n");
            tvRead.append(Html.fromHtml(text));
            tvRead.append("\n");                                	            
        }

        if(ftDev.isOpen()) {
            if(mThreadIsStopped) {
                updateView(true);
                SetConfig(115200, (byte)8, (byte)1, (byte)0, (byte)0);
                ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
                ftDev.restartInTask();
                new Thread(mLoop).start();
            }
        }

        text = "<font color=#cc0029>openDevice finished</font>";
        tvRead.append("\n");
        tvRead.append(Html.fromHtml(text));
        tvRead.append("\n");
        
    }

    private Runnable mLoop = new Runnable() {
        @Override
        public void run() {
            int i;
            int readSize;
            mThreadIsStopped = false;
            while(true) {
                if(mThreadIsStopped) {
//                    String text = "<font color=#FF9640>mLoop stopped, break</font>";
//                    tvRead.append("\n");
//                    tvRead.append(Html.fromHtml(text));
//                    tvRead.append("\n");
                    break;
                }

/*                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                }
*/
                synchronized (ftDev) {
                    readSize = ftDev.getQueueStatus();
                    if(readSize>0) {
                        mReadSize = readSize;
                        if(mReadSize > READBUF_SIZE) {
                            mReadSize = READBUF_SIZE;
                        }
                        ftDev.read(rbuf,mReadSize);

                        // cannot use System.arraycopy
                        for(i=0; i<mReadSize; i++) {
                            rchar[i] = (char)rbuf[i];
                        }
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                tvRead.append(String.copyValueOf(rchar,0,mReadSize));
//                                // scroll down
//                                tvReadScroll.fullScroll(View.FOCUS_DOWN);
                                tvReadScroll.post(new Runnable() {

                                	   @Override
                                	   public void run() {
                                		   tvReadScroll.fullScroll(View.FOCUS_DOWN);
                                	   }
                                	});                                
                            }
                        });

                    } // end of if(readSize>0)
                } // end of synchronized
            }
        }
    };

    private void closeDevice() {
    	Log.d(TAG, "Close Device");
        mThreadIsStopped = true;
        updateView(false);
        if(ftDev != null) {
            ftDev.close();
        }
    }

    private void updateView(boolean on) {
        if(on) {
            btOpen.setEnabled(false);
            btWrite.setEnabled(true);
            btClose.setEnabled(true);
        } else {
            btOpen.setEnabled(true);
            btWrite.setEnabled(false);
            btClose.setEnabled(false);
        }
    }

    public void SetConfig(int baud, byte dataBits, byte stopBits, byte parity, byte flowControl) {
        if (ftDev.isOpen() == false) {
//            Log.e(TAG, "SetConfig: device not open");
//            String text = "<font color=#85004B>SetConfig: device not open</font>";
//            tvRead.append("\n");
//            tvRead.append(Html.fromHtml(text));
//            tvRead.append("\n");
//
            return;
        }

        // configure our port
        // reset to UART mode for 232 devices
        ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);

        ftDev.setBaudRate(baud);

        switch (dataBits) {
        case 7:
            dataBits = D2xxManager.FT_DATA_BITS_7;
            break;
        case 8:
            dataBits = D2xxManager.FT_DATA_BITS_8;
            break;
        default:
            dataBits = D2xxManager.FT_DATA_BITS_8;
            break;
        }

        switch (stopBits) {
        case 1:
            stopBits = D2xxManager.FT_STOP_BITS_1;
            break;
        case 2:
            stopBits = D2xxManager.FT_STOP_BITS_2;
            break;
        default:
            stopBits = D2xxManager.FT_STOP_BITS_1;
            break;
        }

        switch (parity) {
        case 0:
            parity = D2xxManager.FT_PARITY_NONE;
            break;
        case 1:
            parity = D2xxManager.FT_PARITY_ODD;
            break;
        case 2:
            parity = D2xxManager.FT_PARITY_EVEN;
            break;
        case 3:
            parity = D2xxManager.FT_PARITY_MARK;
            break;
        case 4:
            parity = D2xxManager.FT_PARITY_SPACE;
            break;
        default:
            parity = D2xxManager.FT_PARITY_NONE;
            break;
        }

        ftDev.setDataCharacteristics(dataBits, stopBits, parity);

        short flowCtrlSetting;
        switch (flowControl) {
        case 0:
            flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
            break;
        case 1:
            flowCtrlSetting = D2xxManager.FT_FLOW_RTS_CTS;
            break;
        case 2:
            flowCtrlSetting = D2xxManager.FT_FLOW_DTR_DSR;
            break;
        case 3:
            flowCtrlSetting = D2xxManager.FT_FLOW_XON_XOFF;
            break;
        default:
            flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
            break;
        }

        // TODO : flow ctrl: XOFF/XOM
        // TODO : flow ctrl: XOFF/XOM
        ftDev.setFlowControl(flowCtrlSetting, (byte) 0x0b, (byte) 0x0d);
    }

    // done when ACTION_USB_DEVICE_ATTACHED
    @Override
    protected void onNewIntent(Intent intent) {
        openDevice();
    };

    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                // never come here(when attached, go to onNewIntent)
                openDevice();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                closeDevice();
            }
        }
    };

}
