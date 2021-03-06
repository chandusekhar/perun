package cz.metacentrum.perun.core.impl.modules.attributes;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.core.api.Resource;
import cz.metacentrum.perun.core.api.User;
import cz.metacentrum.perun.core.api.exceptions.WrongAttributeValueException;
import cz.metacentrum.perun.core.api.exceptions.WrongReferenceAttributeValueException;
import cz.metacentrum.perun.core.bl.AttributesManagerBl;
import cz.metacentrum.perun.core.bl.GroupsManagerBl;
import cz.metacentrum.perun.core.bl.MembersManagerBl;
import cz.metacentrum.perun.core.bl.PerunBl;
import cz.metacentrum.perun.core.bl.ResourcesManagerBl;
import cz.metacentrum.perun.core.bl.UsersManagerBl;
import cz.metacentrum.perun.core.impl.PerunSessionImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import java.util.List;

import static cz.metacentrum.perun.core.impl.modules.attributes.urn_perun_member_attribute_def_def_o365EmailAddresses_mu.UCO_ATTRIBUTE;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests attribute module.
 *
 * @author Martin Kuba makub@ics.muni.cz
 */
@SuppressWarnings("unchecked")
public class urn_perun_member_attribute_def_def_o365EmailAddresses_muTest {

	private urn_perun_member_attribute_def_def_o365EmailAddresses_mu classInstance;
	private PerunSessionImpl session;
	private Attribute attributeToCheck;
	private final String uco = "123456";
	private final User user = new User(10, "Joe", "Doe", "W.", "", "");
	private final Member member = new Member(1, user.getId());
	private AttributesManagerBl am;

	@Before
	public void setUp() throws Exception {
		classInstance = new urn_perun_member_attribute_def_def_o365EmailAddresses_mu();
		//prepare mocks
		session = mock(PerunSessionImpl.class);
		PerunBl perunBl = mock(PerunBl.class);
		am = mock(AttributesManagerBl.class);
		UsersManagerBl um = mock(UsersManagerBl.class);
		Attribute ucoAttr = mock(Attribute.class);
		when(session.getPerunBl()).thenReturn(perunBl);
		when(perunBl.getAttributesManagerBl()).thenReturn(am);
		when(perunBl.getUsersManagerBl()).thenReturn(um);
		when(um.getUserById(session, member.getUserId())).thenReturn(user);
		when(am.getAttribute(session, user, UCO_ATTRIBUTE)).thenReturn(ucoAttr);
		when(ucoAttr.getValue()).thenReturn(uco);
		when(ucoAttr.valueAsString()).thenReturn(uco);
		when(um.getUserById(session, member.getUserId())).thenReturn(user);
		when(am.getPerunBeanIdsForUniqueAttributeValue(eq(session), argThat(new BeanAttributeMatcher("member"))))
				.thenReturn(Sets.newHashSet(new Pair<>(member.getId(), 0)));
		when(am.getPerunBeanIdsForUniqueAttributeValue(eq(session), argThat(new BeanAttributeMatcher("group_resource"))))
				.thenReturn(Sets.newHashSet());
		attributeToCheck = new Attribute(classInstance.getAttributeDefinition());
		attributeToCheck.setId(101);
	}

	/**
	 * Mockito ArgumentMatcher that matches attributes for a given PerunBean type, for example "member" or "group_resource".
	 */
	public static class BeanAttributeMatcher implements ArgumentMatcher<Attribute> {

		private final String beanName;

		BeanAttributeMatcher(String beanName) {
			this.beanName = beanName;
		}

		@Override
		public boolean matches(Attribute argument) {
			return beanName.equals(argument.getNamespace().split(":")[2]);
		}

		@Override
		public String toString() {
			return "Attribute in namespace urn:perun:" + beanName;
		}
	}

	@Test
	public void fillAttribute() throws Exception {
		System.out.println("fillAttribute()");
		Attribute attribute = classInstance.fillAttribute(session, member, classInstance.getAttributeDefinition());
		Object attributeValue = attribute.getValue();
		assertThat(attributeValue, is(notNullValue()));
		List<String> expectedValue = Lists.newArrayList(uco + "@muni.cz");
		assertThat(attributeValue, equalTo(expectedValue));

		//check that value generated by fillAttribute() is acceptable for checkAttributeSemantics()
		classInstance.checkAttributeSemantics(session, member, attribute);
	}

	@Test(expected = WrongReferenceAttributeValueException.class)
	public void testCheckNull() throws Exception {
		System.out.println("testCheckNull()");
		attributeToCheck.setValue(null);
		classInstance.checkAttributeSemantics(session, member, attributeToCheck);
	}

	@Test(expected = WrongAttributeValueException.class)
	public void testCheckType() throws Exception {
		System.out.println("testCheckType()");
		attributeToCheck.setValue("AAA");
		classInstance.checkAttributeSyntax(session, member, attributeToCheck);
	}

	@Test(expected = WrongAttributeValueException.class)
	public void testCheckEmailSyntax() throws Exception {
		System.out.println("testCheckEmailSyntax()");
		attributeToCheck.setValue(Lists.newArrayList("my@example.com", "a/-+"));
		classInstance.checkAttributeSyntax(session, member, attributeToCheck);
	}

	@Test(expected = WrongAttributeValueException.class)
	public void testCheckDuplicates() throws Exception {
		System.out.println("testCheckDuplicates()");
		attributeToCheck.setValue(Lists.newArrayList("my@example.com", "aaa@bbb.com", "my@example.com"));
		classInstance.checkAttributeSyntax(session, member, attributeToCheck);
	}

	@Test
	public void testCheckMissingUco() throws Exception {
		System.out.println("testCheckMissingUco()");
		attributeToCheck.setValue(Lists.newArrayList("my@example.com", "aaa@bbb.com"));
		try {
			classInstance.checkAttributeSemantics(session, member, attributeToCheck);
			fail("should throw WrongReferenceAttributeValueException");
		} catch (WrongReferenceAttributeValueException ex) {
			assertThat(ex.getMessage(), endsWith("does not contain 123456@muni.cz"));
		}
	}

	@Test
	public void testCorrectSemantics() throws Exception {
		System.out.println("testCorrectSemantics()");
		attributeToCheck.setValue(Lists.newArrayList("my@example.com", "aaa@bbb.com", uco + "@muni.cz"));
		classInstance.checkAttributeSemantics(session, member, attributeToCheck);
	}

	@Test
	public void testCorrectSyntax() throws Exception {
		System.out.println("testCorrectSyntax()");
		attributeToCheck.setValue(Lists.newArrayList("my@example.com", "aaa@bbb.com", uco + "@muni.cz"));
		classInstance.checkAttributeSyntax(session, member, attributeToCheck);
	}

	@Test
	public void testClashMember() throws Exception {
		System.out.println("testClashMember()");
		when(am.getPerunBeanIdsForUniqueAttributeValue(eq(session), argThat(new BeanAttributeMatcher("member"))))
				.thenReturn(Sets.newHashSet(new Pair<>(1000, 0)));
		Member memberWithDuplicateEmail = mock(Member.class);
		MembersManagerBl membersManagerBl = mock(MembersManagerBl.class);
		when(session.getPerunBl().getMembersManagerBl()).thenReturn(membersManagerBl);
		when(session.getPerunBl().getMembersManagerBl().getMemberById(session, 1000)).thenReturn(memberWithDuplicateEmail);
		attributeToCheck.setValue(Lists.newArrayList("my@example.com", uco + "@muni.cz"));
		try {
			classInstance.checkAttributeSemantics(session, member, attributeToCheck);
			fail("should throw WrongReferenceAttributeValueException");
		} catch (WrongReferenceAttributeValueException ex) {
			assertThat(ex.getMessage(), endsWith("some of the email addresses are already assigned."));
		}
	}

	@Test
	public void testClashGroupResource() throws Exception {
		System.out.println("testClashGroupResource()");
		when(am.getPerunBeanIdsForUniqueAttributeValue(eq(session), argThat(new BeanAttributeMatcher("group_resource"))))
				.thenReturn(Sets.newHashSet(new Pair<>(55, 66)));
		Group groupWithDuplicateEmail = mock(Group.class);
		Resource resourceWithDuplicateEmail = mock(Resource.class);
		ResourcesManagerBl resourcesManagerBl = mock(ResourcesManagerBl.class);
		GroupsManagerBl groupsManagerBl = mock(GroupsManagerBl.class);
		when(session.getPerunBl().getGroupsManagerBl()).thenReturn(groupsManagerBl);
		when(session.getPerunBl().getResourcesManagerBl()).thenReturn(resourcesManagerBl);
		when(session.getPerunBl().getGroupsManagerBl().getGroupById(session, 55)).thenReturn(groupWithDuplicateEmail);
		when(session.getPerunBl().getResourcesManagerBl().getResourceById(session, 66)).thenReturn(resourceWithDuplicateEmail);
		attributeToCheck.setValue(Lists.newArrayList("my@example.com", uco + "@muni.cz"));
		try {
			classInstance.checkAttributeSemantics(session, member, attributeToCheck);
			fail("should throw WrongReferenceAttributeValueException");
		} catch (WrongReferenceAttributeValueException ex) {
			assertThat(ex.getMessage(), endsWith("some of the email addresses are already assigned."));
		}
	}
}
