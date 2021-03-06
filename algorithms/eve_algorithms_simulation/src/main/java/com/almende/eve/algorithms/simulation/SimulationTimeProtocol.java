/*
 * Copyright: Almende B.V. (2014), Rotterdam, The Netherlands
 * License: The Apache Software License, Version 2.0
 */
package com.almende.eve.algorithms.simulation;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.almende.eve.capabilities.handler.Handler;
import com.almende.eve.protocol.Meta;
import com.almende.eve.protocol.jsonrpc.RpcBasedProtocol;
import com.almende.eve.protocol.jsonrpc.formats.Caller;
import com.almende.eve.protocol.jsonrpc.formats.JSONMessage;
import com.almende.eve.protocol.jsonrpc.formats.JSONRequest;
import com.almende.eve.protocol.jsonrpc.formats.JSONResponse;
import com.almende.eve.protocol.jsonrpc.formats.Params;
import com.almende.util.TypeUtil;
import com.almende.util.jackson.JOM;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class SimulationTimeProtocol.
 */
public class SimulationTimeProtocol implements RpcBasedProtocol {
	private static final Logger				LOG				= Logger.getLogger(SimulationTimeProtocol.class
																	.getName());
	private static final TypeUtil<Tracer>	TRACER			= new TypeUtil<Tracer>() {};
	private SimulationTimeProtocolConfig	params			= null;

	private Set<Tracer>						outboundTracers	= new HashSet<Tracer>();
	private Set<Tracer>						inboundTracers	= new HashSet<Tracer>();
	private Map<String, Boolean>			inboundRequests	= new HashMap<String, Boolean>();
	private Handler<Caller>					caller			= null;

	/**
	 * Instantiates a new inbox protocol.
	 *
	 * @param params
	 *            the params
	 * @param handle
	 *            the handle
	 */
	public SimulationTimeProtocol(final ObjectNode params,
			final Handler<Object> handle) {
		this.params = SimulationTimeProtocolConfig.decorate(params);
	}

	@Override
	public ObjectNode getParams() {
		return this.params;
	}

	@Override
	public void setCaller(Handler<Caller> caller) {
		this.caller = caller;
	}

	@Override
	public void delete() {}

	private void receiveTracerReport(final Tracer tracer) {
		synchronized (outboundTracers) {
			outboundTracers.remove(tracer);
		}
	}

	private void storeOutboundTracer(final Tracer tracer) {
		synchronized (outboundTracers) {
			outboundTracers.add(tracer);
		}
	}

	private void storeInboundTracer(final Tracer tracer) {
		synchronized (inboundTracers) {
			inboundTracers.add(tracer);
		}
	}

	private boolean doTracer() {
		synchronized (inboundRequests) {
			return !inboundRequests.isEmpty();
		}
	}

	private Collection<Tracer> checkTracers() {
		synchronized (outboundTracers) {
			synchronized (inboundRequests) {
				if (outboundTracers.isEmpty() && inboundRequests.isEmpty()) {
					return inboundTracers;
				}
				return null;
			}
		}
	}

	private Tracer createTracer() {
		final Tracer tracer = new Tracer();
		tracer.setOwner(caller.get().getSenderUrls().get(0));
		return tracer;
	}

	private void sendReports(final Collection<Tracer> tracers,
			JSONResponse resp, final URI peer) {
		if (tracers == null) {
			return;
		}
		synchronized (tracers) {
			final Iterator<Tracer> iter = tracers.iterator();

			while (iter.hasNext()) {
				final Tracer tracer = iter.next();
				final ObjectNode extra = JOM.createObjectNode();
				extra.set("@simtracerreport",
						JOM.getInstance().valueToTree(tracer));
				if (resp != null && tracer.getOwner().equals(peer)) {
					if (resp.getExtra() == null) {
						resp.setExtra(extra);
					} else {
						resp.getExtra().setAll(extra);
					}
					resp = null;
				} else {
					final Params params = new Params();
					params.set("tracer", JOM.getInstance().valueToTree(tracer));
					final JSONRequest message = new JSONRequest(
							"scheduler.receiveTracerReport", params);
					message.setExtra(extra);
					try {
						caller.get().call(tracer.getOwner(), message);
					} catch (IOException e) {
						LOG.log(Level.WARNING, "Failed to send tracerreport", e);
					}
				}
				iter.remove();
			}
		}
	}

	private boolean handleReport(final JsonNode report,
			final JSONMessage message, final URI peer) {
		final Tracer tracer = TRACER.inject(report);
		synchronized (outboundTracers) {
			if (outboundTracers.contains(tracer)) {
				receiveTracerReport(tracer);
				sendReports(checkTracers(), null, peer);
				if (message.isRequest()) {
					return false;
				}
			}
		}
		return true;
	}

	private void pullTracer(final JSONRequest message) {
		final ObjectNode extra = message.getExtra();
		final Object tracerObj = extra.remove("@simtracer");
		if (tracerObj != null) {
			synchronized (inboundRequests) {
				Tracer tracer = TRACER.inject(tracerObj);
				storeInboundTracer(tracer);
				if (message.getId() != null && !message.getId().isNull()) {
					inboundRequests.put(message.getId().asText(), false);
				} else {
					message.setId(JOM.getInstance().valueToTree(tracer.getId()));
					inboundRequests.put(message.getId().asText(), true);
				}
			}
		}
	}

	private void addTracer(final JSONRequest message) {
		final Tracer tracer = createTracer();
		final ObjectNode extra = JOM.createObjectNode();
		extra.set("@simtracer", JOM.getInstance().valueToTree(tracer));
		if (message.getExtra() == null) {
			message.setExtra(extra);
		} else {
			message.getExtra().setAll(extra);
		}
		storeOutboundTracer(tracer);
	}

	private boolean handleReplies(final JSONResponse response,
			final String tag, final URI peer) {
		Boolean drop = null;
		synchronized (inboundRequests) {
			drop = inboundRequests.remove(response.getId().textValue());
			if (drop != null && drop && (tag == null || tag.isEmpty())) {
				sendReports(checkTracers(), null, peer);
				// skip forwarding
				return false;
			} else {
				sendReports(checkTracers(), response, peer);
			}
		}
		return true;
	}

	@Override
	public boolean inbound(final Meta msg) {
		// Parse inbound message, check for tracers and/or reports
		JSONMessage message = JSONMessage.jsonConvert(msg.getMsg());
		if (message != null) {
			// Don't parse multiple times.
			msg.setMsg(message);
			if (message.getExtra() != null) {
				final ObjectNode extra = message.getExtra();
				if (message.isRequest()) {
					final JSONRequest request = (JSONRequest) message;
					if (!"scheduler.receiveTracerReport".equals(request
							.getMethod())) {
						pullTracer(request);
					}
				}
				final JsonNode report = extra.remove("@simtracerreport");
				if (report != null) {
					if (!handleReport(report, message, msg.getPeer())) {
						// Not forwarding this (swallowing report)
						return false;
					}
				}
			}
		}
		return msg.nextIn();
	}

	@Override
	public boolean outbound(final Meta msg) {
		if (doTracer()) {
			final JSONMessage message = JSONMessage.jsonConvert(msg.getMsg());
			if (message != null) {
				// Don't parse multiple times.
				msg.setMsg(message);

				if (message.isRequest()) {
					final JSONRequest request = (JSONRequest) message;
					if (!"scheduler.receiveTracerReport".equals(request
							.getMethod())
							&& (request.getExtra() == null || !request
									.getExtra().has("@simtracer"))) {
						addTracer(request);
					}
				} else {
					final JSONResponse response = (JSONResponse) message;
					if (!handleReplies(response, msg.getTag(), msg.getPeer())) {
						// skip forwarding, swallowing reply
						return false;
					}
				}
			}
		}
		return msg.nextOut();
	}
}
