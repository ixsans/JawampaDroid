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

package ixsanslabs.com.jawampatest.server;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import ixsanslabs.com.jawampatest.data.Constants;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import ws.wamp.jawampa.ApplicationError;
import ws.wamp.jawampa.WampClient;
import ws.wamp.jawampa.WampClientBuilder;
import ws.wamp.jawampa.WampRouter;
import ws.wamp.jawampa.WampRouterBuilder;
import ws.wamp.jawampa.connection.IWampConnectorProvider;
import ws.wamp.jawampa.transport.netty.NettyWampClientConnectorProvider;
import ws.wamp.jawampa.transport.netty.SimpleWampWebsocketListener;

public class ServerTest {
    WampRouterBuilder routerBuilder;
    WampRouter router;
    SimpleWampWebsocketListener server;
    IWampConnectorProvider connectorProvider;
    WampClientBuilder builder;

    Subscription eventSubscription;


    public static void main(String[] args) {
        new ServerTest().start();
    }

    public void start() {
        runServer();
        runServerClient();
    }

    private void runServer(){
      routerBuilder = new WampRouterBuilder();

        try {
            routerBuilder.addRealm(Constants.REALM);
            router = routerBuilder.build();
        } catch (ApplicationError e1) {
            e1.printStackTrace();
            return;
        }

        URI serverUri = URI.create(Constants.CHAT_SERVER_URL);


        connectorProvider = new NettyWampClientConnectorProvider();
        builder = new WampClientBuilder();


        try {
            server = new SimpleWampWebsocketListener(router, serverUri, null);
            server.start();

            builder.withConnectorProvider(connectorProvider)
                    .withUri(Constants.CHAT_SERVER_URL)
                    .withRealm(Constants.REALM)
                    .withInfiniteReconnects()
                    .withReconnectInterval(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }


    private void runServerClient(){
        final WampClient serverClientListener;
        try {
            builder.withConnectorProvider(connectorProvider)
                    .withUri(Constants.CHAT_SERVER_URL)
                    .withRealm(Constants.REALM)
                    .withInfiniteReconnects()
                    .withReconnectInterval(3, TimeUnit.SECONDS);
            serverClientListener = builder.build();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        serverClientListener.statusChanged().subscribe(new Action1<WampClient.State>() {
            @Override
            public void call(WampClient.State t1) {

                if (t1 instanceof WampClient.ConnectedState) {

                    System.out.println("Server is running...");

                    /*serverClientListener.registerProcedure(Constants.EVENT_NEW_MESSAGE).subscribe(new Action1<Request>() {
                        @Override
                        public void call(Request request) {
                            if (request.arguments() == null) {
                                try {
                                    request.replyError(new ApplicationError(ApplicationError.INVALID_PARAMETER));
                                } catch (ApplicationError e) {
                                    e.printStackTrace();
                                }
                            } else {
                                String a = request.arguments().get(0).asText();
                                request.reply(a);
                            }
                        }
                    });*/


                    eventSubscription = serverClientListener.makeSubscription("test.event", String.class)
                            .subscribe(new Action1<String>() {
                                @Override
                                public void call(String t1) {
                                    System.out.println("Received event test.event with value " + t1);
                                }
                            }, new Action1<Throwable>() {
                                @Override
                                public void call(Throwable t1) {
                                    System.out.println("Completed event test.event with error " + t1);
                                }
                            }, new Action0() {
                                @Override
                                public void call() {
                                    System.out.println("Completed event test.event");
                                }
                            });
                }
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable t) {
                System.out.println("Session1 ended with error " + t);
            }
        }, new Action0() {
            @Override
            public void call() {
                System.out.println("Session1 ended normally");
            }
        });


        serverClientListener.open();


        waitUntilKeypressed();
        System.out.println("Stopping subscription");
        if (eventSubscription != null)
            eventSubscription.unsubscribe();


        waitUntilKeypressed();
        System.out.println("Closing router");
        router.close().toBlocking().last();
        server.stop();

        waitUntilKeypressed();
        System.out.println("Closing the server client listener");
        //serverClientListener.close().toBlocking().last();

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
