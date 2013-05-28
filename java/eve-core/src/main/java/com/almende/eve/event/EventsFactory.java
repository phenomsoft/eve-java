package com.almende.eve.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.almende.eve.agent.Agent;
import com.almende.eve.rpc.annotation.Access;
import com.almende.eve.rpc.annotation.AccessType;
import com.almende.eve.rpc.annotation.Name;
import com.almende.eve.rpc.jsonrpc.JSONRPCException;
import com.almende.eve.rpc.jsonrpc.JSONRequest;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class EventsFactory {
	Agent	myAgent	= null;
	
	public EventsFactory(Agent agent) {
		this.myAgent = agent;
	}
	
	/**
	 * Retrieve the list with subscriptions on given event.
	 * If there are no subscriptions for this event, an empty list is returned
	 * 
	 * @param event
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private List<Callback> getSubscriptions(String event) {
		Map<String, List<Callback>> allSubscriptions = (Map<String, List<Callback>>) myAgent
				.getState().get("subscriptions");
		if (allSubscriptions != null) {
			List<Callback> eventSubscriptions = allSubscriptions.get(event);
			if (eventSubscriptions != null) {
				return eventSubscriptions;
			}
		}
		
		return new ArrayList<Callback>();
	}
	
	/**
	 * Store a list with subscriptions for an event
	 * 
	 * @param event
	 * @param subscriptions
	 */
	@SuppressWarnings("unchecked")
	private void putSubscriptions(String event, List<Callback> subscriptions) {
		HashMap<String, List<Callback>> allSubscriptions = (HashMap<String, List<Callback>>) myAgent
				.getState().get("subscriptions");
		if (allSubscriptions == null) {
			allSubscriptions = new HashMap<String, List<Callback>>();
		}
		allSubscriptions.put(event, subscriptions);
		myAgent.getState().put("subscriptions", allSubscriptions);
	}
	
	/**
	 * Subscribe to an other agents event
	 * 
	 * @param url
	 * @param event
	 * @param callbackMethod
	 * @return subscriptionId
	 * @throws Exception
	 */
	public String subscribe(String url, String event, String callbackMethod)
			throws Exception {
		return subscribe(url, event, callbackMethod, null);
	}
	
	/**
	 * Subscribe to an other agents event
	 * 
	 * @param url
	 * @param event
	 * @param callbackMethod
	 * @return subscriptionId
	 * @throws Exception
	 */
	public String subscribe(String url, String event, String callbackMethod,
			ObjectNode callbackParams) throws Exception {
		String method = "createSubscription";
		ObjectNode params = JOM.createObjectNode();
		params.put("event", event);
		params.put("callbackUrl", myAgent.getFirstUrl());
		params.put("callbackMethod", callbackMethod);
		if (callbackParams != null) {
			params.put("callbackParams", callbackParams);
		}
		
		// TODO: store the agents subscriptions locally
		return myAgent.send(url, method, params, String.class);
	}
	
	/**
	 * Unsubscribe from an other agents event
	 * 
	 * @param url
	 * @param subscriptionId
	 * @throws Exception
	 */
	public void unsubscribe(String url, String subscriptionId) throws Exception {
		String method = "deleteSubscription";
		ObjectNode params = JOM.createObjectNode();
		params.put("subscriptionId", subscriptionId);
		myAgent.send(url, method, params);
	}
	
	/**
	 * Unsubscribe from an other agents event
	 * 
	 * @param url
	 * @param event
	 * @param callbackMethod
	 * @throws Exception
	 */
	public void unsubscribe(String url, String event, String callbackMethod)
			throws Exception {
		String method = "deleteSubscription";
		ObjectNode params = JOM.createObjectNode();
		params.put("event", event);
		params.put("callbackUrl", myAgent.getFirstUrl());
		params.put("callbackMethod", callbackMethod);
		myAgent.send(url, method, params);
	}
	
	/**
	 * Trigger an event
	 * 
	 * @param event
	 * @param params
	 *            An ObjectNode, Map, or POJO
	 * @throws Exception
	 * @throws JSONRPCException
	 */
	@Access(AccessType.UNAVAILABLE)
	final public void trigger(@Name("event") String event,
			@Name("params") Object params) throws Exception {
		// TODO: user first url is very dangerous! can cause a mismatch
		String url = myAgent.getFirstUrl();
		List<Callback> subscriptions = new ArrayList<Callback>();
		
		if (event.equals("*")) {
			throw new Exception("Cannot trigger * event");
		}
		
		// send a trigger to the agent factory
		myAgent.getAgentFactory().getEventLogger()
				.log(myAgent.getId(), event, params);
		
		// retrieve subscriptions from the event
		List<Callback> valueEvent = getSubscriptions(event);
		subscriptions.addAll(valueEvent);
		
		// retrieve subscriptions from the all event "*"
		List<Callback> valueAll = getSubscriptions("*");
		subscriptions.addAll(valueAll);
		
		// TODO: smartly remove double entries?
		ObjectNode callbackParams = JOM.createObjectNode();
		callbackParams.put("agent", url);
		callbackParams.put("event", event);
		if (params instanceof JsonNode) {
			callbackParams.put("params", (ObjectNode) params);
		} else {
			ObjectNode jsonParams = JOM.getInstance().convertValue(params,
					ObjectNode.class);
			callbackParams.put("params", jsonParams);
		}
		
		for (Callback subscription : subscriptions) {
			// create a task to send this trigger.
			// This way, it is sent asynchronously and cannot block this
			// trigger method
			callbackParams.put("subscriptionId", subscription.id); // TODO: test
																	// if
																	// changing
																	// subscriptionId
																	// works
																	// with
																	// multiple
																	// tasks
			
			ObjectNode taskParams = JOM.createObjectNode();
			taskParams.put("url", subscription.url);
			taskParams.put("method", subscription.method);
			if (subscription.params != null) {
				ObjectNode parms = (ObjectNode) JOM.getInstance()
						.readTree(subscription.params).get("params");
				callbackParams
						.put("params", parms.putAll((ObjectNode) callbackParams
								.get("params")));
			} else {
				System.err.println("subscription.params empty");
			}
			taskParams.put("params", callbackParams);
			JSONRequest request = new JSONRequest("doTrigger", taskParams);
			long delay = 0;
			myAgent.getScheduler().createTask(request, delay);
		}
	}
	
	public String createSubscription(String event, String callbackUrl,
			String callbackMethod, ObjectNode params) {
		List<Callback> subscriptions = getSubscriptions(event);
		for (Callback subscription : subscriptions) {
			if (subscription.url == null || subscription.method == null) {
				continue;
			}
			if (subscription.url.equals(callbackUrl)
					&& subscription.method.equals(callbackMethod)
					&& ((subscription.params == null && params == null) || subscription.params != null)
					&& subscription.params.equals(params)) {
				// The callback already exists. do not duplicate it
				return subscription.id;
			}
		}
		
		// the callback does not yet exist. create it and store it
		String subscriptionId = UUID.randomUUID().toString();
		Callback callback = new Callback(subscriptionId, callbackUrl,
				callbackMethod, params);
		subscriptions.add(callback);
		
		// store the subscriptions
		putSubscriptions(event, subscriptions);
		
		return subscriptionId;
	}
	
	public void deleteSubscription(String subscriptionId, String event,
			String callbackUrl, String callbackMethod) {
		@SuppressWarnings("unchecked")
		HashMap<String, List<Callback>> allSubscriptions = (HashMap<String, List<Callback>>) myAgent.getState()
				.get("subscriptions");
		if (allSubscriptions == null) {
			return;
		}
		
		for (Entry<String, List<Callback>> entry : allSubscriptions.entrySet()) {
			String subscriptionEvent = entry.getKey();
			List<Callback> subscriptions = entry.getValue();
			if (subscriptions != null) {
				int i = 0;
				while (i < subscriptions.size()) {
					Callback subscription = subscriptions.get(i);
					boolean matched = false;
					if (subscriptionId != null
							&& subscriptionId.equals(subscription.id)) {
						// callback with given subscriptionId is found
						matched = true;
					} else if (callbackUrl != null
							&& callbackUrl.equals(subscription.url)) {
						if ((callbackMethod == null || callbackMethod
								.equals(subscription.method))
								&& (event == null || event
										.equals(subscriptionEvent))) {
							// callback with matching properties is found
							matched = true;
						}
					}
					
					if (matched) {
						subscriptions.remove(i);
					} else {
						i++;
					}
				}
			}
			// TODO: cleanup event list when empty
		}
		
		// store state again
		myAgent.getState().put("subscriptions", allSubscriptions);
	}
}
