/*
 * Copyright: Almende B.V. (2014), Rotterdam, The Netherlands
 * License: The Apache Software License, Version 2.0
 */
package com.almende.eve.agent;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.almende.eve.capabilities.handler.Handler;
import com.almende.eve.capabilities.handler.SimpleHandler;
import com.almende.eve.scheduling.Scheduler;
import com.almende.eve.scheduling.SchedulerFactory;
import com.almende.eve.state.State;
import com.almende.eve.state.StateFactory;
import com.almende.eve.transform.rpc.RpcTransform;
import com.almende.eve.transform.rpc.RpcTransformFactory;
import com.almende.eve.transform.rpc.annotation.Access;
import com.almende.eve.transform.rpc.annotation.AccessType;
import com.almende.eve.transform.rpc.annotation.Namespace;
import com.almende.eve.transform.rpc.jsonrpc.JSONRequest;
import com.almende.eve.transform.rpc.jsonrpc.JSONResponse;
import com.almende.eve.transport.Receiver;
import com.almende.eve.transport.Router;
import com.almende.eve.transport.Transport;
import com.almende.eve.transport.TransportConfig;
import com.almende.eve.transport.TransportFactory;
import com.almende.util.callback.AsyncCallback;
import com.almende.util.callback.SyncCallback;
import com.almende.util.uuid.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class Agent.
 */
@Access(AccessType.PUBLIC)
public class Agent implements Receiver {
	private static final Logger	LOG			= Logger.getLogger(Agent.class
													.getName());
	private String				agentId		= null;
	private ObjectNode			config		= null;
	private State				state		= null;
	private Transport			transport	= null;
	private Scheduler			scheduler	= null;
	protected RpcTransform		rpc			= RpcTransformFactory
													.get(new SimpleHandler<Object>(
															this));
	protected Handler<Receiver>	receiver	= new SimpleHandler<Receiver>(this);
	
	/**
	 * Instantiates a new agent.
	 */
	public Agent() {
	}
	
	/**
	 * Instantiates a new agent.
	 * 
	 * @param config
	 *            the config
	 */
	public Agent(final ObjectNode config) {
		setConfig(config);
	}
	
	/**
	 * Sets the config.
	 * 
	 * @param config
	 *            the new config
	 */
	public void setConfig(final ObjectNode config) {
		this.config = config.deepCopy();
		loadConfig(false);
	}
	
	/**
	 * Sets the config.
	 * 
	 * @param config
	 *            the new config
	 * @param onBoot
	 *            the on boot flag
	 */
	public void setConfig(final ObjectNode config, final boolean onBoot) {
		this.config = config.deepCopy();
		loadConfig(onBoot);
	}
	
	/**
	 * Gets the id.
	 * 
	 * @return the id
	 */
	public String getId() {
		return agentId;
	}
	
	/**
	 * Gets the config.
	 * 
	 * @return the config
	 */
	public ObjectNode getConfig() {
		return config;
	}
	
	protected void loadConfig(final boolean onBoot) {
		if (config.has("id")) {
			agentId = config.get("id").asText();
		} else {
			agentId = new UUID().toString();
		}
		if (config.has("scheduler")) {
			final ObjectNode schedulerConfig = (ObjectNode) config
					.get("scheduler");
			if (agentId != null && schedulerConfig.has("state")) {
				final ObjectNode stateConfig = (ObjectNode) schedulerConfig
						.get("state");
				if (!stateConfig.has("id")) {
					stateConfig.put("id", "scheduler_" + agentId);
				}
			}
			scheduler = SchedulerFactory
					.getScheduler(schedulerConfig, receiver);
		}
		if (config.has("state")) {
			final ObjectNode stateConfig = (ObjectNode) config.get("state");
			if (agentId != null && !stateConfig.has("id")) {
				stateConfig.put("id", agentId);
			}
			state = StateFactory.getState(stateConfig);
		}
		if (config.has("transport")) {
			if (config.get("transport").isArray()) {
				final Router router = new Router();
				final Iterator<JsonNode> iter = config.get("transport")
						.iterator();
				while (iter.hasNext()) {
					TransportConfig transconfig = new TransportConfig(
							(ObjectNode) iter.next());
					if (transconfig.get("id") == null) {
						transconfig.put("id", agentId);
					}
					router.register(TransportFactory.getTransport(transconfig,
							receiver));
				}
				transport = router;
			} else {
				TransportConfig transconfig = new TransportConfig(
						(ObjectNode) config.get("transport"));
				if (transconfig.get("id") == null) {
					transconfig.put("id", agentId);
				}
				transport = TransportFactory
						.getTransport(transconfig, receiver);
			}
			if (onBoot){
				try {
					transport.connect();
				} catch (IOException e) {
					LOG.log(Level.WARNING,"Couldn't connect transports on boot",e);
				}
			}
		}
	}
	
	@Override
	public void receive(final Object msg, final URI senderUrl, final String tag) {
		final JSONResponse response = rpc.invoke(msg, senderUrl);
		if (response != null) {
			try {
				transport.send(senderUrl, response.toString(), tag);
			} catch (final IOException e) {
				LOG.log(Level.WARNING, "Couldn't send message", e);
			}
		}
	}
	
	/**
	 * Sets the scheduler.
	 * 
	 * @param scheduler
	 *            the new scheduler
	 */
	@JsonIgnore
	public void setScheduler(Scheduler scheduler) {
		this.scheduler = scheduler;
	}
	
	/**
	 * Gets the scheduler.
	 * 
	 * @return the scheduler
	 */
	@Namespace("scheduler")
	@JsonIgnore
	public Scheduler getScheduler() {
		return scheduler;
	}
	
	/**
	 * Sets the state.
	 * 
	 * @param state
	 *            the new state
	 */
	@JsonIgnore
	public void setState(State state) {
		this.state = state;
	}
	
	/**
	 * Gets the state.
	 * 
	 * @return the state
	 */
	@Access(AccessType.UNAVAILABLE)
	@JsonIgnore
	public State getState() {
		return state;
	}
	
	/**
	 * Connect all transports.
	 * 
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	@Access(AccessType.UNAVAILABLE)
	public void connect() throws IOException{
		this.transport.connect();
	}
	
	/**
	 * Send async.
	 * 
	 * @param <T>
	 *            the generic type
	 * @param url
	 *            the url
	 * @param method
	 *            the method
	 * @param params
	 *            the params
	 * @param callback
	 *            the callback
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	@Access(AccessType.UNAVAILABLE)
	protected final <T> void send(final URI url, final String method,
			final ObjectNode params, final AsyncCallback<T> callback)
			throws IOException {
		final JSONRequest request = rpc.buildMsg(method, params, callback);
		transport.send(url, request.toString(), null);
	}
	
	/**
	 * Send async.
	 * 
	 * @param <T>
	 *            the generic type
	 * @param url
	 *            the url
	 * @param method
	 *            the method
	 * @param params
	 *            the params
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	@Access(AccessType.UNAVAILABLE)
	protected final <T> void send(final URI url, final String method,
			final ObjectNode params) throws IOException {
		final JSONRequest request = rpc.buildMsg(method, params, null);
		transport.send(url, request.toString(), null);
	}
	
	/**
	 * Send sync, expecting a response.
	 * 
	 * @param <T>
	 *            the generic type
	 * @param url
	 *            the url
	 * @param method
	 *            the method
	 * @param params
	 *            the params
	 * @return response
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	@Access(AccessType.UNAVAILABLE)
	protected final <T> T sendSync(final URI url, final String method,
			final ObjectNode params) throws IOException {
		SyncCallback<T> callback = new SyncCallback<T>();
		final JSONRequest request = rpc.buildMsg(method, params, callback);
		transport.send(url, request.toString(), null);
		try {
			return callback.get();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
	
}
