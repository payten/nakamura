#!/usr/bin/env ruby


require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers

class TC_Kern2183Test < Test::Unit::TestCase
  include SlingTest

  def test_demote_manager_to_member
    m = Time.now.to_nsec
    g1 = create_group("g-testgroup1-#{m}")
    g1_manager = create_group("g-testgroup1-#{m}-manager")
    g1_member = create_group("g-testgroup1-#{m}-member")
    g1.add_member(@s, g1_member.name, "group")
    g1.add_member(@s, g1_manager.name, "group")
    pav = create_user("pav-#{m}")
    
  
    # adding pav as a manager of the group
    res = g1_manager.add_member_viewer(@s, pav.name)
    assert_equal("200", res.code, "Expected first add to succeed")
    assert(g1_manager.has_member(@s, pav.name), "Expected member name in group")
    
    #demote pav by removing as manager, then adding as member
    g1_manager.remove_member_viewer(@s, pav.name)
    g1.remove_member_viewer(@s, pav.name)
    g1_member.add_member_viewer(@s, pav.name)
    assert(g1_member.has_member(@s, pav.name), "The member pseudo-group should have this person in it")
    assert_equal(false, g1_manager.has_member(@s, pav.name), "This person should no longer be in the manager pseudo-group")
    res = @s.execute_get(@s.url_for("/~#{g1.name}.acl.json"))
    res = @s.execute_get(@s.url_for("/~#{g1_manager.name}.acl.json"))
    
    # try to promote self, which will not be allowed
    @s.switch_user(pav)
    res = g1_manager.add_member_viewer(@s, pav.name)
    assert_equal("403", res.code, "Expected 403 (forbidden) when attempting to promote self to manager.")
    
    # in spite of demotion, should still be able to see participants of the manager group
    res = @s.execute_get(@s.url_for(Group.url_for(g1_manager.name) + ".everyone.json"))
    assert_equal("200", res.code, "Expected to still be able to view the participants of the manager group")
    
    # system/me shows group memberships. Check that this is up-to-date
    res = @s.execute_get(@s.url_for("/system/me"))
    assert_equal("200", res.code, "Me servlet should return successfully")
    groups = JSON.parse(res.body)["groups"]
    assert_equal(2, groups.size, "Should have two groups")
    
    # now we'll remove the user from the group entirely
    @s.switch_user(User.admin_user())
    g1_member.remove_member_viewer(@s, pav.name)
    g1.remove_member_viewer(@s, pav.name)
    
    # in spite of being removed, should still be able to see participants of the manager group
    @s.switch_user(pav)
    res = @s.execute_get(@s.url_for(Group.url_for(g1_manager.name) + ".everyone.json"))
    assert_equal("200", res.code, "Expected to still be able to view the participants of the manager group")
    
    
  end
  
  def test_remove_member_will_not_change_parent_group_acl
    # create a group and the standard pseudo-groups
    m = Time.now.to_nsec
    g1 = create_group("g-testgroup1-#{m}")
    g1_manager = create_group("g-testgroup1-#{m}-manager")
    g1_member = create_group("g-testgroup1-#{m}-member")
    g1.add_member_viewer(@s, g1_member.name)
    g1.add_member_viewer(@s, g1_manager.name)
    
    # add a group as a member of the members pseudo-group
    child_group = create_group("child-group-#{m}")
    g1_member.add_member_viewer(@s, child_group.name)
    
    res = @s.execute_get(@s.url_for("/~#{g1.name}.acl.json"))
    assert_equal(5, JSON.parse(res.body).size)
    
    g1_member.remove_member_viewer(@s, child_group.name)
    g1.remove_member_viewer(@s, child_group.name)
    
    res = @s.execute_get(@s.url_for("/~#{g1.name}.acl.json"))
    assert_equal(5, JSON.parse(res.body).size)
  end
  
  def test_self_demote_manager_to_member
    m = Time.now.to_nsec
    g1 = create_group("g-testgroup1-#{m}")
    g1_manager = create_group("g-testgroup1-#{m}-manager")
    g1_member = create_group("g-testgroup1-#{m}-member")
    g1.add_member(@s, g1_member.name, "group")
    g1.add_manager(@s, g1_manager.name)
    g1_manager.add_manager(@s, g1_manager.name)
    g1_member.add_manager(@s, g1_manager.name)
    pav = create_user("pav-#{m}")
    
    # adding pav as a manager of the group
    res = g1_manager.add_member_viewer(@s, pav.name)
    res = @s.execute_get(@s.url_for("/~#{g1.name}.acl.json"))
    assert_equal("200", res.code, "Expected first add to succeed")
    assert(g1_manager.has_member(@s, pav.name), "Expected member name in group")
    
    #pav demote himself by adding as member, then removing as manager
    @s.switch_user(pav)
    g1_member.add_member_viewer(@s, pav.name)
    g1_manager.remove_member_viewer(@s, pav.name)
    g1.remove_member_viewer(@s, pav.name)
    assert(g1_member.has_member(@s, pav.name), "The member pseudo-group should have this person in it")
    assert_equal(false, g1_manager.has_member(@s, pav.name), "This person should no longer be in the manager pseudo-group")
    res = @s.execute_get(@s.url_for("/~#{g1.name}.acl.json"))
    res = @s.execute_get(@s.url_for("/~#{g1_manager.name}.acl.json"))
    
    # try to promote self, which will not be allowed
    res = g1_manager.add_member_viewer(@s, pav.name)
    assert_equal("403", res.code, "Expected 403 (forbidden) when attempting to promote another.")
    
    # in spite of demotion, should still be able to see participants of the manager group
    res = @s.execute_get(@s.url_for(Group.url_for(g1_manager.name) + ".everyone.json"))
    assert_equal("200", res.code, "Expected to still be able to view the participants of the manager group")
    
    # system/me shows group memberships. Check that this is up-to-date
    res = @s.execute_get(@s.url_for("/system/me"))
    assert_equal("200", res.code, "Me servlet should return successfully")
    groups = JSON.parse(res.body)["groups"]
    assert_equal(2, groups.size, "Should have two groups")
    
    # now we'll remove the user from the group entirely
    @s.switch_user(User.admin_user())
    g1_member.remove_member_viewer(@s, pav.name)
    g1.remove_member_viewer(@s, pav.name)
    
    # in spite of being removed, should still be able to see participants of the manager group
    @s.switch_user(pav)
    res = @s.execute_get(@s.url_for(Group.url_for(g1_manager.name) + ".everyone.json"))
    assert_equal("200", res.code, "Expected to still be able to view the participants of the manager group")
    
  end
  
end


