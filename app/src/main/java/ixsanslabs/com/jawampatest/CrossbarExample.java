package ixsanslabs.com.jawampatest;
/*
 * Copyright 2014 Matthias Einwag
 *
 * The jawampa authors license this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */


import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ixsanslabs.com.jawampatest.data.Constants;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import ws.wamp.jawampa.WampClient;
import ws.wamp.jawampa.WampClientBuilder;
import ws.wamp.jawampa.WampError;
import ws.wamp.jawampa.connection.IWampConnectorProvider;
import ws.wamp.jawampa.transport.netty.NettyWampClientConnectorProvider;

/**
 * A demo application that demonstrates all features of WAMP
 * and equals the demo applications of crossbar.io
 */
public class CrossbarExample {

    String url;
    String realm;

    WampClient client;
    IWampConnectorProvider connectorProvider = new NettyWampClientConnectorProvider();
    Subscription addProcSubscription;
    Subscription counterPublication;
    Subscription onHelloSubscription;

    // Scheduler for this example
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Scheduler rxScheduler = Schedulers.from(executor);

    static final int TIMER_INTERVAL = 1000; // 1s
    int counter = 0;

    CrossbarExample() throws Exception {
        this.url = "ws://192.168.43.168:8080/ws1";
        this.realm = "realm1";
    }

    /**
     * Main entry point of the example
     * @param args Expected are 2 arguments: The url of the router and the name of the realm
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        /*if (args.length != 2) {
            System.out.println("Need 2 commandline arguments: Router URL and ream name");
            return;
        }*/

        new CrossbarExample().run();
    }

    void run() {

        WampClientBuilder builder = new WampClientBuilder();
        try {
            builder.withUri(url)
                    .withRealm(realm)
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
                .observeOn(rxScheduler)
                .subscribe(new Action1<WampClient.State>() {
                    @Override
                    public void call(WampClient.State t1) {
                        System.out.println("Session status changed to " + t1);

                        if (t1 instanceof WampClient.ConnectedState) {
                            System.out.println("Connected!");

                            /*client.registerProcedure("com.example.onhello").subscribe(new Action1<Request>() {
                                @Override
                                public void call(Request request) {
                                    if (request.arguments() == null ) {
                                        try {
                                            request.replyError(new ApplicationError(ApplicationError.INVALID_PARAMETER));
                                        } catch (ApplicationError e) {
                                            e.printStackTrace();
                                        }
                                    } else {
                                        String a = request.arguments().get(0).asText();
                                        request.reply("say,"+a);
                                    }
                                }
                            });*/


                            // SUBSCRIBE to a topic and receive events
                            onHelloSubscription = client.makeSubscription(Constants.EVENT_NEW_MESSAGE, String.class)
                                    .observeOn(rxScheduler)
                                    .subscribe(new Action1<String>() {
                                        @Override
                                        public void call(String msg) {
                                            System.out.println("event for 'onhello' received: " + msg);
                                        }
                                    }, new Action1<Throwable>() {
                                        @Override
                                        public void call(Throwable e) {
                                            System.out.println("failed to subscribe 'onhello': " + e);
                                        }
                                    }, new Action0() {
                                        @Override
                                        public void call() {
                                            System.out.println("'onhello' subscription ended");
                                        }
                                    });

                            /*final JSONObject jsonMessage = new JSONObject();
                            try {
                                jsonMessage.put("username", "Server");
                                jsonMessage.put("message", "hallo dari server..");

                            }catch (JSONException e){

                            }*/

                            String message = "{\"username\": \"ahmad\", \"message\": \"hai dari server\"}";
                                client.publish(Constants.EVENT_NEW_MESSAGE, message)
                                    .observeOn(rxScheduler)
                                    .subscribe(new Action1<Long>() {
                                        @Override
                                        public void call(Long t1) {
                                            System.out.println("published message: " + "halo dari server");
                                        }
                                    }, new Action1<Throwable>() {
                                        @Override
                                        public void call(Throwable e) {
                                            System.out.println("Error during publishing message: " + e);
                                        }
                                    });

                        }
                        else if (t1 instanceof WampClient.DisconnectedState) {
                            closeSubscriptions();
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable t) {
                        System.out.println("Session ended with error " + t);
                    }
                }, new Action0() {
                    @Override
                    public void call() {
                        System.out.println("Session ended normally");
                    }
                });

        client.open();

        waitUntilKeypressed();
        System.out.println("Shutting down");
        closeSubscriptions();
        client.close();
        try {
            client.getTerminationFuture().get();
        } catch (Exception e) {}

        executor.shutdown();
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

    private void waitUntilKeypressed() {
        try {
            System.in.read();
            while (System.in.available() > 0) {
                System.in.read();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}