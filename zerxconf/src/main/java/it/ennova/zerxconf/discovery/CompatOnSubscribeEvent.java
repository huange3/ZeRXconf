package it.ennova.zerxconf.discovery;


import android.content.Context;
import android.net.wifi.WifiManager;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.net.InetAddress;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

import it.ennova.zerxconf.common.OnSubscribeEvent;
import it.ennova.zerxconf.exceptions.NsdException;
import it.ennova.zerxconf.model.NetworkServiceDiscoveryInfo;
import it.ennova.zerxconf.utils.NsdUtils;
import rx.Subscriber;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

import static it.ennova.zerxconf.model.NetworkServiceDiscoveryInfo.*;

public class CompatOnSubscribeEvent implements OnSubscribeEvent<NetworkServiceDiscoveryInfo> {

    private JmDNS jmDNS;
    private Subscriber<? super NetworkServiceDiscoveryInfo> subscriber;
    private final String protocol;
    private Context context;
    private final String SUFFIX = "local.";

    private final Action0 dismissAction = new Action0() {
        @Override
        public void call() {
            if (jmDNS != null) {
                try {
                    jmDNS.removeServiceListener(protocol, listener);
                    jmDNS.close();
                } catch (Exception e) {}
            }
        }
    };

    private ServiceListener listener = new ServiceListener() {
        @Override
        public void serviceAdded(ServiceEvent event) {
            event.getDNS().requestServiceInfo(event.getType(), event.getName());
        }

        @Override
        public void serviceRemoved(ServiceEvent serviceEvent) {
            if (!subscriber.isUnsubscribed()) {
                subscriber.onNext(from(serviceEvent.getInfo(), REMOVED));
            }
        }

        @Override
        public void serviceResolved(ServiceEvent serviceEvent) {
            if (!subscriber.isUnsubscribed()) {
                subscriber.onNext(from(serviceEvent.getInfo(), ADDED));
            }
        }
    };

    public CompatOnSubscribeEvent(@NonNull Context context, @NonNull String protocol) {
        this.protocol = buildProtocolFrom(protocol);
        this.context = context;
    }

    private String buildProtocolFrom(String protocol) {
        if (!protocol.endsWith(SUFFIX)) {
            return addSuffixTo(protocol);
        } else {
            return protocol;
        }
    }

    @NonNull
    private String addSuffixTo(String protocol) {
        if (!protocol.endsWith(".")) {
            return protocol + "." + SUFFIX;
        }
        return protocol + SUFFIX;
    }

    @Override
    public Action0 onCompleted() {
        return dismissAction;
    }

    @Override
    public void call(Subscriber<? super NetworkServiceDiscoveryInfo> subscriber) {
        this.subscriber = subscriber;

        if (!NsdUtils.isValidProtocol(protocol)) {
            subscriber.onError(new NsdException());
        } else {
            startSubscription(subscriber);
        }
    }

    private void startSubscription(Subscriber<? super NetworkServiceDiscoveryInfo> subscriber) {
        final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        try {
            final InetAddress inetAddress = buildAddress(wifiManager);
            jmDNS = JmDNS.create(inetAddress, inetAddress.toString());
            jmDNS.addServiceListener(protocol, listener);
        } catch (IOException e) {
            subscriber.onError(e);
        }

        subscriber.add(Subscriptions.create(dismissAction));
    }

    private InetAddress buildAddress(WifiManager wifiManager) throws IOException {
        int baseAddress = wifiManager.getConnectionInfo().getIpAddress();
        byte[] converted = new byte[] { (byte) (baseAddress & 0xff), (byte) (baseAddress >> 8 & 0xff),
                (byte) (baseAddress >> 16 & 0xff), (byte) (baseAddress >> 24 & 0xff) };

        return InetAddress.getByAddress(converted);
    }
}
