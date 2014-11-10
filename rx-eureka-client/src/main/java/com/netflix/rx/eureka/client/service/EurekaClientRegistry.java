package com.netflix.rx.eureka.client.service;

import javax.inject.Inject;
import java.util.Set;

import com.netflix.rx.eureka.client.metric.EurekaClientMetricFactory;
import com.netflix.rx.eureka.client.transport.TransportClient;
import com.netflix.rx.eureka.data.Source;
import com.netflix.rx.eureka.interests.ChangeNotification;
import com.netflix.rx.eureka.interests.Interest;
import com.netflix.rx.eureka.registry.Delta;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.registry.EurekaRegistryImpl;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.service.EurekaService;
import com.netflix.rx.eureka.service.InterestChannel;
import rx.Observable;
import rx.Subscriber;

/**
 * An implementation of {@link EurekaRegistry} to be used by the eureka client.
 *
 * This registry abstracts the {@link InterestChannel} interaction from the consumers of this registry and transparently
 * reconnects when a channel is broken.
 *
 * <h2>Storage</h2>
 *
 * This registry uses {@link EurekaRegistryImpl} for actual data storage.
 *
 * <h2>Reconnects</h2>
 *
 * Whenever the used {@link InterestChannel} is broken, this class holds the last known registry information till the
 * time it is successfully able to reconnect and relay the last know interest set to the new {@link InterestChannel}.
 * On a successful reconnect, the old registry data is disposed and the registry is created afresh from the instance
 * stream from the new {@link InterestChannel}
 *
 * @author Nitesh Kant
 */
public class EurekaClientRegistry implements EurekaRegistry<InstanceInfo> {

    private final EurekaService service;
    private final EurekaRegistry<InstanceInfo> registry;
    private final ClientInterestChannel interestChannel;

    @Inject
    public EurekaClientRegistry(final TransportClient readServerClient, EurekaClientMetricFactory metricFactory) {
        registry = new EurekaRegistryImpl(metricFactory.getRegistryMetrics());

        service = EurekaServiceImpl.forReadServer(registry, readServerClient, metricFactory);
        interestChannel = (ClientInterestChannel) service.newInterestChannel();
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        return registry.register(instanceInfo);
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo, Source source) {
        return registry.register(instanceInfo, source);
    }

    @Override
    public Observable<Void> unregister(String instanceId) {
        return registry.unregister(instanceId);
    }

    @Override
    public Observable<Void> unregister(String instanceId, Source source) {
        return registry.unregister(instanceId, source);
    }

    @Override
    public Observable<Void> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        return registry.update(updatedInfo, deltas);
    }

    @Override
    public Observable<Void> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas, Source source) {
        return registry.update(updatedInfo, deltas, source);
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest) {
        return registry.forSnapshot(interest);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        Observable toReturn = interestChannel
                .appendInterest(interest).cast(ChangeNotification.class)
                .mergeWith(registry.forInterest(interest));

        return toReturn;
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, Source source) {
        throw new IllegalStateException("Origin filtering not supported by EurekaClientRegistry");
    }

    @Override
    public Observable<Void> shutdown() {
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                interestChannel.close();
                service.shutdown();  // service will shutdown registry and transport clients
            }
        });
    }

    @Override
    public String toString() {
        return registry.toString();
    }
}
