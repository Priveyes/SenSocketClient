/*
 * Copyright 2018 shenhuanet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shenhua.libs.sensockettcp.nio;

import com.shenhua.libs.sensocketcore.IConnectListener;
import com.shenhua.libs.sensockettcp.TcpAddress;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * Created by shenhua on 2018/4/11.
 *
 * @author shenhua
 *         Email shenhuanet@126.com
 */
public final class TcpNioConnector {

    private final int STATE_CLOSE = 0x1;
    private final int STATE_CONNECT_START = 0x2;
    private final int STATE_CONNECT_SUCCESS = 0x3;

    private long connectTimeout = 10000;
    private TcpAddress[] mAddress = null;
    private int mConnectIndex = -1;
    private int state = STATE_CLOSE;

    private TcpNioClient mClient;
    private IConnectListener mIConnectListener;
    private TcpNioReadWriteProcessor mSocketProcessor;

    private TcpNioConnectListener mProxyConnectStatusListener = new TcpNioConnectListener() {
        @Override
        public synchronized void onConnectSuccess(TcpNioReadWriteProcessor mSocketProcessor, SocketChannel socketChannel) throws IOException {
            if (mSocketProcessor != TcpNioConnector.this.mSocketProcessor) {
                if (null != mSocketProcessor) {
                    mSocketProcessor.close();
                }
                return;
            }

            state = STATE_CONNECT_SUCCESS;
            mClient.init(socketChannel);

            if (null != mIConnectListener) {
                mIConnectListener.onConnectionSuccess();
            }
        }

        @Override
        public synchronized void onConnectFailed(TcpNioReadWriteProcessor mSocketProcessor) {
            if (mSocketProcessor != TcpNioConnector.this.mSocketProcessor) {
                if (null != mSocketProcessor) {
                    mSocketProcessor.close();
                }
                return;
            }

            state = STATE_CLOSE;
            connect();
        }
    };

    public TcpNioConnector(TcpNioClient mClient, IConnectListener mConnectListener) {
        this.mClient = mClient;
        this.mIConnectListener = mConnectListener;
    }

    public boolean isConnected() {
        return state == STATE_CONNECT_SUCCESS;
    }

    private boolean isConnecting() {
        return state == STATE_CONNECT_START;
    }

    private boolean isClosed() {
        return state == STATE_CLOSE;
    }

    public void setConnectAddress(TcpAddress[] tcpArray) {
        this.mConnectIndex = -1;
        this.mAddress = tcpArray;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public synchronized void connect() {
        startConnect();
    }

    public synchronized void reconnect() {
        stopConnect();
        if (mConnectIndex + 1 >= mAddress.length || mConnectIndex + 1 < 0) {
            mConnectIndex = -1;
        }
        startConnect();
    }

    public synchronized void disconnect() {
        stopConnect();
    }

    public void checkConnect() {
        if (null == mSocketProcessor) {
            startConnect();
        } else if (!isConnected() && !isConnecting()) {
            startConnect();
        } else {
            if (isConnected()) {
                mSocketProcessor.wakeUp();
            }
        }
    }

    private void startConnect() {
        if (!isClosed()) {
            return;
        }

        mConnectIndex++;
        if (mConnectIndex < mAddress.length && mConnectIndex >= 0) {
            state = STATE_CONNECT_START;
            mSocketProcessor = new TcpNioReadWriteProcessor(mAddress[mConnectIndex].ip, mAddress[mConnectIndex].port, connectTimeout, mClient, mProxyConnectStatusListener);
            mSocketProcessor.start();
        } else {
            mConnectIndex = -1;

            if (null != mIConnectListener) {
                mIConnectListener.onConnectionFailed();
            }
        }
    }

    private void stopConnect() {
        state = STATE_CLOSE;
        mClient.onClose();

        if (null != mSocketProcessor) {
            mSocketProcessor.close();
            mSocketProcessor = null;
        }
    }
}