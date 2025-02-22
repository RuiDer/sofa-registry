/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.registry.server.data.bootstrap;

import com.alipay.sofa.registry.remoting.bolt.exchange.BoltExchange;
import com.alipay.sofa.registry.remoting.exchange.Exchange;
import com.alipay.sofa.registry.remoting.jersey.exchange.JerseyExchange;
import com.alipay.sofa.registry.server.data.cache.DataServerCache;
import com.alipay.sofa.registry.server.data.change.DataChangeHandler;
import com.alipay.sofa.registry.server.data.change.event.DataChangeEventCenter;
import com.alipay.sofa.registry.server.data.change.notify.BackUpNotifier;
import com.alipay.sofa.registry.server.data.change.notify.IDataChangeNotifier;
import com.alipay.sofa.registry.server.data.change.notify.SessionServerNotifier;
import com.alipay.sofa.registry.server.data.change.notify.TempPublisherNotifier;
import com.alipay.sofa.registry.server.data.correction.LocalDataServerCleanHandler;
import com.alipay.sofa.registry.server.data.datasync.AcceptorStore;
import com.alipay.sofa.registry.server.data.datasync.SyncDataService;
import com.alipay.sofa.registry.server.data.datasync.sync.LocalAcceptorStore;
import com.alipay.sofa.registry.server.data.datasync.sync.Scheduler;
import com.alipay.sofa.registry.server.data.datasync.sync.StoreServiceFactory;
import com.alipay.sofa.registry.server.data.datasync.sync.SyncDataServiceImpl;
import com.alipay.sofa.registry.server.data.event.AfterWorkingProcess;
import com.alipay.sofa.registry.server.data.event.EventCenter;
import com.alipay.sofa.registry.server.data.event.handler.AfterWorkingProcessHandler;
import com.alipay.sofa.registry.server.data.event.handler.DataServerChangeEventHandler;
import com.alipay.sofa.registry.server.data.event.handler.LocalDataServerChangeEventHandler;
import com.alipay.sofa.registry.server.data.event.handler.MetaServerChangeEventHandler;
import com.alipay.sofa.registry.server.data.event.handler.StartTaskEventHandler;
import com.alipay.sofa.registry.server.data.node.DataNodeStatus;
import com.alipay.sofa.registry.server.data.remoting.DataNodeExchanger;
import com.alipay.sofa.registry.server.data.remoting.MetaNodeExchanger;
import com.alipay.sofa.registry.server.data.remoting.dataserver.DataServerConnectionFactory;
import com.alipay.sofa.registry.server.data.remoting.dataserver.GetSyncDataHandler;
import com.alipay.sofa.registry.server.data.remoting.dataserver.handler.DataSyncServerConnectionHandler;
import com.alipay.sofa.registry.server.data.remoting.dataserver.handler.FetchDataHandler;
import com.alipay.sofa.registry.server.data.remoting.dataserver.handler.NotifyDataSyncHandler;
import com.alipay.sofa.registry.server.data.remoting.dataserver.handler.NotifyFetchDatumHandler;
import com.alipay.sofa.registry.server.data.remoting.dataserver.handler.NotifyOnlineHandler;
import com.alipay.sofa.registry.server.data.remoting.dataserver.handler.SyncDataHandler;
import com.alipay.sofa.registry.server.data.remoting.dataserver.task.AbstractTask;
import com.alipay.sofa.registry.server.data.remoting.dataserver.task.ConnectionRefreshTask;
import com.alipay.sofa.registry.server.data.remoting.dataserver.task.ReNewNodeTask;
import com.alipay.sofa.registry.server.data.remoting.handler.AbstractClientHandler;
import com.alipay.sofa.registry.server.data.remoting.handler.AbstractServerHandler;
import com.alipay.sofa.registry.server.data.remoting.metaserver.DefaultMetaServiceImpl;
import com.alipay.sofa.registry.server.data.remoting.metaserver.IMetaServerService;
import com.alipay.sofa.registry.server.data.remoting.metaserver.MetaServerConnectionFactory;
import com.alipay.sofa.registry.server.data.remoting.metaserver.handler.ServerChangeHandler;
import com.alipay.sofa.registry.server.data.remoting.metaserver.handler.StatusConfirmHandler;
import com.alipay.sofa.registry.server.data.remoting.metaserver.task.ConnectionRefreshMetaTask;
import com.alipay.sofa.registry.server.data.remoting.sessionserver.SessionServerConnectionFactory;
import com.alipay.sofa.registry.server.data.remoting.sessionserver.disconnect.DisconnectEventHandler;
import com.alipay.sofa.registry.server.data.remoting.sessionserver.forward.ForwardService;
import com.alipay.sofa.registry.server.data.remoting.sessionserver.forward.ForwardServiceImpl;
import com.alipay.sofa.registry.server.data.remoting.sessionserver.handler.ClientOffHandler;
import com.alipay.sofa.registry.server.data.remoting.sessionserver.handler.DataServerConnectionHandler;
import com.alipay.sofa.registry.server.data.remoting.sessionserver.handler.GetDataHandler;
import com.alipay.sofa.registry.server.data.remoting.sessionserver.handler.GetDataVersionsHandler;
import com.alipay.sofa.registry.server.data.remoting.sessionserver.handler.PublishDataHandler;
import com.alipay.sofa.registry.server.data.remoting.sessionserver.handler.SessionServerRegisterHandler;
import com.alipay.sofa.registry.server.data.remoting.sessionserver.handler.UnPublishDataHandler;
import com.alipay.sofa.registry.server.data.resource.DataDigestResource;
import com.alipay.sofa.registry.server.data.resource.HealthResource;
import com.alipay.sofa.registry.util.PropertySplitter;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author qian.lqlq
 * @version $Id: DataServerBeanConfiguration.java, v 0.1 2018-01-11 15:08 qian.lqlq Exp $
 */
@Configuration
@Import(DataServerInitializer.class)
@EnableConfigurationProperties
public class DataServerBeanConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DataServerBootstrap dataServerBootstrap() {
        return new DataServerBootstrap();
    }

    @Configuration
    protected static class DataServerBootstrapConfigConfiguration {

        @Bean
        public CommonConfig commonConfig() {
            return new CommonConfig();
        }

        @Bean
        public DataServerConfig dataServerBootstrapConfig(CommonConfig commonConfig) {
            return new DataServerConfig(commonConfig);
        }

        @Bean
        public DataNodeStatus dataNodeStatus() {
            return new DataNodeStatus();
        }

        @Bean(name = "PropertySplitter")
        public PropertySplitter propertySplitter() {
            return new PropertySplitter();
        }
    }

    @Configuration
    public static class SessionRemotingConfiguration {
        @Bean
        public Exchange jerseyExchange() {
            return new JerseyExchange();
        }

        @Bean
        public Exchange boltExchange() {
            return new BoltExchange();
        }

        @Bean
        public MetaNodeExchanger metaNodeExchanger() {
            return new MetaNodeExchanger();
        }

        @Bean
        public DataNodeExchanger dataNodeExchanger() {
            return new DataNodeExchanger();
        }

        @Bean
        public DataServerCache dataServerCache() {
            return new DataServerCache();
        }

        @Bean
        public ForwardService forwardService() {
            return new ForwardServiceImpl();
        }

        @Bean
        public SessionServerConnectionFactory sessionServerConnectionFactory() {
            return new SessionServerConnectionFactory();
        }

        @Bean
        public DataServerConnectionFactory dataServerConnectionFactory() {
            return new DataServerConnectionFactory();
        }

        @Bean
        public MetaServerConnectionFactory metaServerConnectionFactory() {
            return new MetaServerConnectionFactory();
        }

        @Bean(name = "serverHandlers")
        public Collection<AbstractServerHandler> serverHandlers(DataServerConfig dataServerBootstrapConfig) {
            Collection<AbstractServerHandler> list = new ArrayList<>();
            list.add(getDataHandler());
            list.add(clientOffHandler());
            list.add(getDataVersionsHandler());
            list.add(publishDataProcessor(dataServerBootstrapConfig));
            list.add(sessionServerRegisterHandler());
            list.add(unPublishDataHandler());
            list.add(dataServerConnectionHandler());
            return list;
        }

        @Bean(name = "serverSyncHandlers")
        public Collection<AbstractServerHandler> serverSyncHandlers(DataServerConfig dataServerBootstrapConfig) {
            Collection<AbstractServerHandler> list = new ArrayList<>();
            list.add(getDataHandler());
            list.add(publishDataProcessor(dataServerBootstrapConfig));
            list.add(unPublishDataHandler());
            list.add(notifyFetchDatumHandler());
            list.add(notifyOnlineHandler());
            list.add(syncDataHandler());
            list.add(dataSyncServerConnectionHandler());
            return list;
        }

        @Bean(name = "dataClientHandlers")
        public Collection<AbstractClientHandler> dataClientHandlers() {
            Collection<AbstractClientHandler> list = new ArrayList<>();
            list.add(notifyDataSyncHandler());
            list.add(fetchDataHandler());
            return list;
        }

        @Bean(name = "metaClientHandlers")
        public Collection<AbstractClientHandler> metaClientHandlers() {
            Collection<AbstractClientHandler> list = new ArrayList<>();
            list.add(serverChangeHandler());
            list.add(statusConfirmHandler());
            return list;
        }

        @Bean
        public AbstractServerHandler dataServerConnectionHandler() {
            return new DataServerConnectionHandler();
        }

        @Bean
        public AbstractServerHandler dataSyncServerConnectionHandler() {
            return new DataSyncServerConnectionHandler();
        }

        @Bean
        public AbstractServerHandler getDataHandler() {
            return new GetDataHandler();
        }

        @Bean
        public AbstractServerHandler getDataVersionsHandler() {
            return new GetDataVersionsHandler();
        }

        @Bean
        public AbstractServerHandler clientOffHandler() {
            return new ClientOffHandler();
        }

        @Bean
        public AbstractServerHandler publishDataProcessor(DataServerConfig dataServerBootstrapConfig) {
            return new PublishDataHandler(dataServerBootstrapConfig);
        }

        @Bean
        public AbstractServerHandler sessionServerRegisterHandler() {
            return new SessionServerRegisterHandler();
        }

        @Bean
        public AbstractServerHandler unPublishDataHandler() {
            return new UnPublishDataHandler();
        }

        @Bean
        public AbstractServerHandler notifyFetchDatumHandler() {
            return new NotifyFetchDatumHandler();
        }

        @Bean
        public AbstractServerHandler notifyOnlineHandler() {
            return new NotifyOnlineHandler();
        }

        @Bean
        public AbstractServerHandler syncDataHandler() {
            return new SyncDataHandler();
        }

        @Bean
        public AbstractClientHandler notifyDataSyncHandler() {
            return new NotifyDataSyncHandler();
        }

        @Bean
        public AbstractClientHandler fetchDataHandler() {
            return new FetchDataHandler();
        }

        @Bean
        public AbstractClientHandler serverChangeHandler() {
            return new ServerChangeHandler();
        }

        @Bean
        public AbstractClientHandler statusConfirmHandler() {
            return new StatusConfirmHandler();
        }
    }

    @Configuration
    public static class DataServerNotifyBeanConfiguration {
        @Bean
        public DataChangeHandler dataChangeHandler() {
            return new DataChangeHandler();
        }

        @Bean
        public SessionServerNotifier sessionServerNotifier() {
            return new SessionServerNotifier();
        }

        @Bean
        public TempPublisherNotifier tempPublisherNotifier() {
            return new TempPublisherNotifier();
        }

        @Bean
        public BackUpNotifier backUpNotifier() {
            return new BackUpNotifier();
        }

        @Bean(name = "dataChangeNotifiers")
        public List<IDataChangeNotifier> dataChangeNotifiers(DataServerConfig dataServerBootstrapConfig) {
            List<IDataChangeNotifier> list = new ArrayList<>();
            list.add(sessionServerNotifier());
            list.add(tempPublisherNotifier());
            list.add(backUpNotifier());
            return list;
        }
    }

    @Configuration
    public static class DataServerSyncBeanConfiguration {

        @Bean
        public SyncDataService syncDataService() {
            return new SyncDataServiceImpl();
        }

        @Bean
        public AcceptorStore localAcceptorStore() {
            return new LocalAcceptorStore();
        }

        @Bean
        public Scheduler syncDataScheduler() {
            return new Scheduler();
        }

        @Bean
        public StoreServiceFactory storeServiceFactory() {
            return new StoreServiceFactory();
        }
    }

    @Configuration
    public static class DataServerEventBeanConfiguration {

        @Bean
        public DataServerChangeEventHandler dataServerChangeEventHandler() {
            return new DataServerChangeEventHandler();
        }

        @Bean
        public LocalDataServerChangeEventHandler localDataServerChangeEventHandler() {
            return new LocalDataServerChangeEventHandler();
        }

        @Bean
        public MetaServerChangeEventHandler metaServerChangeEventHandler() {
            return new MetaServerChangeEventHandler();
        }

        @Bean
        public StartTaskEventHandler startTaskEventHandler() {
            return new StartTaskEventHandler();
        }

        @Bean
        public LocalDataServerCleanHandler localDataServerCleanHandler() {
            return new LocalDataServerCleanHandler();
        }

        @Bean
        public GetSyncDataHandler getSyncDataHandler() {
            return new GetSyncDataHandler();
        }

        @Bean
        public DisconnectEventHandler disconnectEventHandler() {
            return new DisconnectEventHandler();
        }

        @Bean
        public EventCenter eventCenter() {
            return new EventCenter();
        }

        @Bean
        public DataChangeEventCenter dataChangeEventCenter() {
            return new DataChangeEventCenter();
        }
    }

    @Configuration
    public static class DataServerRemotingBeanConfiguration {

        @Bean
        public ConnectionRefreshTask connectionRefreshTask() {
            return new ConnectionRefreshTask();
        }

        @Bean
        public ConnectionRefreshMetaTask connectionRefreshMetaTask() {
            return new ConnectionRefreshMetaTask();
        }

        @Bean
        public ReNewNodeTask reNewNodeTask() {
            return new ReNewNodeTask();
        }

        @Bean(name = "tasks")
        public List<AbstractTask> tasks() {
            List<AbstractTask> list = new ArrayList<>();
            list.add(connectionRefreshTask());
            list.add(connectionRefreshMetaTask());
            list.add(reNewNodeTask());
            return list;
        }

        @Bean
        public IMetaServerService metaServerService() {
            return new DefaultMetaServiceImpl();
        }
    }

    @Configuration
    public static class ResourceConfiguration {

        @Bean
        public ResourceConfig jerseyResourceConfig() {
            ResourceConfig resourceConfig = new ResourceConfig();
            resourceConfig.register(JacksonFeature.class);
            return resourceConfig;
        }

        @Bean
        public HealthResource healthResource() {
            return new HealthResource();
        }

        @Bean
        public DataDigestResource dataDigestResource() {
            return new DataDigestResource();
        }
    }

    @Configuration
    public static class AfterWorkingProcessConfiguration {

        @Autowired
        DisconnectEventHandler disconnectEventHandler;

        @Autowired
        AbstractClientHandler  notifyDataSyncHandler;

        @Bean(name = "afterWorkProcessors")
        public List<AfterWorkingProcess> afterWorkingProcessors() {
            List<AfterWorkingProcess> list = new ArrayList<>();
            list.add(disconnectEventHandler);
            list.add((NotifyDataSyncHandler) notifyDataSyncHandler);
            return list;
        }

        @Bean
        public AfterWorkingProcessHandler afterWorkingProcessHandler() {
            return new AfterWorkingProcessHandler();
        }

    }
}