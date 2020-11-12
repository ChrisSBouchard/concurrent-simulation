package cmsc433.p4.actors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.swing.JOptionPane;

import cmsc433.p4.enums.*;
import cmsc433.p4.messages.*;
import cmsc433.p4.util.*;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.AbstractActor.Receive;
import akka.actor.AbstractActor;

public class ResourceManagerActor extends AbstractActor {

	private ActorRef logger;					// Actor to send logging messages to

	/**
	 * Props structure-generator for this class.
	 * @return  Props structure
	 */
	static Props props (ActorRef logger) {
		return Props.create(ResourceManagerActor.class, logger);
	}

	/**
	 * Factory method for creating resource managers
	 * @param logger			Actor to send logging messages to
	 * @param system			Actor system in which manager will execute
	 * @return					Reference to new manager
	 */
	public static ActorRef makeResourceManager (ActorRef logger, ActorSystem system) {
		ActorRef newManager = system.actorOf(props(logger));
		return newManager;
	}

	/**
	 * Sends a message to the Logger Actor
	 * @param msg The message to be sent to the logger
	 */
	public void log (LogMsg msg) {
		logger.tell(msg, getSelf());
	}

	/**
	 * Constructor
	 * 
	 * @param logger			Actor to send logging messages to
	 */
	private ResourceManagerActor(ActorRef logger) {
		super();
		this.logger = logger;
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Object.class, this::onReceive)
				.build();
	}

	// You may want to add data structures for managing local resources and users, storing
	// remote managers, etc.
	//
	private Set<ActorRef> remoteManagers = new HashSet<ActorRef>();
	private HashSet<ActorRef> localUsers = new HashSet<ActorRef>();
	private HashMap<String, Resource> localResources = new HashMap<String, Resource>();
	private HashMap<String, Queue<AccessRequest>> resourceBlockingRequests = new HashMap<String, Queue<AccessRequest>>();
	private HashMap<String, ManagementRequest> futureDisabledResources = new HashMap<String, ManagementRequest>();

	// REMEMBER:  YOU ARE NOT ALLOWED TO CREATE MUTABLE DATA STRUCTURES THAT ARE SHARED BY
	// MULTIPLE ACTORS!

	/* (non-Javadoc)
	 * 
	 * You must provide an implementation of the onReceive() method below.
	 * 
	 * @see akka.actor.AbstractActor#createReceive
	 */

	public void onReceive(Object msg) throws Exception {

		if (msg instanceof AccessRequestMsg) {
			AccessRequestMsg request = (AccessRequestMsg) msg;

			ActorRef replyTo = request.getReplyTo();
			AccessRequest accessRequest = request.getAccessRequest();

			log(LogMsg.makeAccessRequestReceivedLogMsg(replyTo, getSelf(), accessRequest));

			if (localResources.containsKey(accessRequest.getResourceName())) {
				Resource resource = localResources.get(accessRequest.getResourceName());

				// Handle resource disabled
				if (resource.getStatus() == ResourceStatus.DISABLED || futureDisabledResources.containsKey(resource.name)) {
					AccessRequestDeniedMsg response = new AccessRequestDeniedMsg(request, AccessRequestDenialReason.RESOURCE_DISABLED);
					log(LogMsg.makeAccessRequestDeniedLogMsg(replyTo, getSelf(), accessRequest, AccessRequestDenialReason.RESOURCE_DISABLED));
					getSender().tell(response, getSelf());
					return;
				}

				// Handle nonblocking requests
				if (!resourceBlockingRequests.containsKey(resource.getName()) || resourceBlockingRequests.get(resource.getName()).isEmpty()) {
					AccessRequestGrantedMsg response = new AccessRequestGrantedMsg(accessRequest);
					log(LogMsg.makeAccessRequestGrantedLogMsg(replyTo, getSelf(), accessRequest));
					getSender().tell(response, getSelf());
					return;
				} else {
					// Can't handle nonblocking requests if the resource is blocked
					AccessRequestDeniedMsg response = new AccessRequestDeniedMsg(request, AccessRequestDenialReason.RESOURCE_BUSY);
					log(LogMsg.makeAccessRequestDeniedLogMsg(replyTo, getSelf(), accessRequest, AccessRequestDenialReason.RESOURCE_BUSY));
					getSender().tell(response, getSelf());
					return;
				}

			} 

			AccessRequestGrantedMsg response = new AccessRequestGrantedMsg(request);

			getSender().tell(response, getSelf());
		} else if (msg instanceof ManagementRequestMsg) {
			ManagementRequestMsg request = (ManagementRequestMsg) msg;
			ActorRef replyTo = request.getReplyTo();
			ManagementRequest managementRequest = request.getRequest();
			log(LogMsg.makeManagementRequestReceivedLogMsg(replyTo, getSelf(), managementRequest));
			ManagementRequestGrantedMsg response = new ManagementRequestGrantedMsg(request);
			getSender().tell(response, getSelf());
			
		} else if (msg instanceof AddRemoteManagersRequestMsg) {
			AddRemoteManagersRequestMsg request = (AddRemoteManagersRequestMsg) msg;
			
			for (ActorRef remoteManager : request.getManagerList()) {
				remoteManagers.add(remoteManager);
			}
			
			AddRemoteManagersResponseMsg response = new AddRemoteManagersResponseMsg(request);
			
			getSender().tell(response, getSelf());
		} else if (msg instanceof AddLocalUsersRequestMsg) {
			AddLocalUsersRequestMsg request = (AddLocalUsersRequestMsg) msg;
			
			for (ActorRef user : request.getLocalUsers()) {
				localUsers.add(user);
			}
			
			AddLocalUsersResponseMsg response = new AddLocalUsersResponseMsg(request);
			
			getSender().tell(response, getSelf());
		} else if (msg instanceof AddInitialLocalResourcesRequestMsg) {
			AddInitialLocalResourcesRequestMsg request = (AddInitialLocalResourcesRequestMsg) msg;
			
			for (Resource resource : request.getLocalResources()) {
				resource.enable();
				localResources.put(resource.name, resource);
				log(LogMsg.makeLocalResourceCreatedLogMsg(getSelf(), resource.name));
				log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resource.name, ResourceStatus.ENABLED));
			}
			
			AddInitialLocalResourcesResponseMsg response = new AddInitialLocalResourcesResponseMsg(request);
			
			getSender().tell(response, getSelf());
		}

	}


}
