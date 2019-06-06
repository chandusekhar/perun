package cz.metacentrum.perun.ldapc.beans;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cz.metacentrum.perun.core.bl.PerunBl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.Status;
import cz.metacentrum.perun.core.api.User;
import cz.metacentrum.perun.core.api.UserExtSource;
import cz.metacentrum.perun.core.api.exceptions.PerunException;
import cz.metacentrum.perun.ldapc.model.PerunUser;

@Component
public class UserSynchronizer extends AbstractSynchronizer implements ApplicationContextAware {

	private final static Logger log = LoggerFactory.getLogger(UserSynchronizer.class);

	private ApplicationContext context;

	private PerunUser[] perunUser = new PerunUser[5];

	private class SyncUsersWorker implements Runnable {

		public int poolIndex;
		public User user;
		public List<Attribute> attrs;
		public Set<Integer> voIds;
		public List<Group> groups;
		List<UserExtSource> userExtSources;

		public SyncUsersWorker(int poolIndex, User user, List<Attribute> attrs, Set<Integer> voIds, List<Group> groups, List<UserExtSource> userExtSources) {
			this.poolIndex = poolIndex;
			this.user = user;
			this.attrs = attrs;
			this.voIds = voIds;
			this.groups = groups;
			this.userExtSources = userExtSources;
		}

		public void run() {
			try {
				log.debug("Synchronizing user {} with {} attrs", user, attrs.size());
				//perunUser[poolIndex].synchronizeEntry(user, attrs);
				log.debug("Synchronizing user {} with {} VOs and {} groups", user.getId(), voIds.size(), groups.size());
				//perunUser[poolIndex].synchronizeMembership(user, voIds, groups);
				log.debug("Synchronizing user {} with {} extSources", user.getId(), userExtSources.size());
				//perunUser[poolIndex].synchronizePrincipals(user, userExtSources);
				perunUser[poolIndex].synchronizeUser(user, attrs, voIds, groups, userExtSources);
			} catch (PerunException e) {
				log.error("Error synchronizing user", e);

			} catch (Exception e) {
				log.error("Error synchronizing user", e);

			}
		}

	}


	public void synchronizeUsers() {

		PerunBl perun = (PerunBl)ldapcManager.getPerunBl();

		ThreadPoolTaskExecutor syncExecutor = new ThreadPoolTaskExecutor();
		int poolIndex;

		for(poolIndex = 0; poolIndex < perunUser.length; poolIndex++ ) {
			perunUser[poolIndex] = context.getBean("perunUser", PerunUser.class);
		}

		try {

			log.debug("Getting list of users");
			List<User> users = perun.getUsersManagerBl().getUsers(ldapcManager.getPerunSession());

			syncExecutor.setCorePoolSize(5);
			syncExecutor.setMaxPoolSize(8);
			//syncExecutor.setQueueCapacity(30);
			syncExecutor.initialize();

			poolIndex = 0;

			for(User user: users) {

				log.debug("Getting list of attributes for user {}", user.getId());
				List<Attribute> attrs = new ArrayList<Attribute>();
				List<String> attrNames = fillPerunAttributeNames(perunUser[poolIndex].getPerunAttributeNames());
					try {
						//log.debug("Getting attribute {} for user {}", attrName, user.getId());
						attrs.addAll(perun.getAttributesManagerBl().getAttributes(ldapcManager.getPerunSession(), user, attrNames));
						/* very chatty
						if(attr == null) {
							log.debug("Got null for attribute {}", attrName);
						} else if (attr.getValue() == null) {
							log.debug("Got attribute {} with null value", attrName);
						} else {
							log.debug("Got attribute {} with value {}", attrName, attr.getValue().toString());
						}
						*/
					} catch (PerunException e) {
						log.warn("Couldn't get attributes {} for user {}: {}", attrNames, user.getId(), e.getMessage());
					}
				log.debug("Got attributes {}", attrNames.toString());

				try {
					//log.debug("Synchronizing user {} with {} attrs", user, attrs.size());
					//perunUser.synchronizeEntry(user, attrs);

					log.debug("Getting list of member groups for user {}", user.getId());
					Set<Integer> voIds = new HashSet<>();
					List<Member> members = perun.getMembersManagerBl().getMembersByUser(ldapcManager.getPerunSession(), user);
					List<Group> groups = new ArrayList<Group>();
					for(Member member: members) {
						if(member.getStatus().equals(Status.VALID)) {
							voIds.add(member.getVoId());
							groups.addAll(perun.getGroupsManagerBl().getAllGroupsWhereMemberIsActive(ldapcManager.getPerunSession(), member));
						}
					}

					//log.debug("Synchronizing user {} with {} VOs and {} groups", user.getId(), voIds.size(), groups.size());
					//perunUser.synchronizeMembership(user, voIds, groups);

					log.debug("Getting list of extSources for user {}", user.getId());
					List<UserExtSource> userExtSources = perun.getUsersManagerBl().getUserExtSources(ldapcManager.getPerunSession(), user);

					//log.debug("Synchronizing user {} with {} extSources", user.getId(), userExtSources.size());
					//perunUser.synchronizePrincipals(user, userExtSources);

					syncExecutor.execute(new SyncUsersWorker(poolIndex, user, attrs, voIds, groups, userExtSources));

				} catch (PerunException e) {
					log.error("Error synchronizing user", e);
				}

				poolIndex = (poolIndex + 1) % perunUser.length;
			}

		} catch (PerunException e) {
			log.error("Error synchronizing users", e);
		} finally {
			syncExecutor.shutdown();
			for(poolIndex = 0; poolIndex < perunUser.length; poolIndex++) {
				perunUser[poolIndex] = null;
			}
		}

	}


	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		this.context = context;
	}

}
