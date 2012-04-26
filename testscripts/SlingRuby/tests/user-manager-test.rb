#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'nakamura/search'
require 'test/unit.rb'
include SlingSearch
include SlingUsers

class TC_UserManagerTest < Test::Unit::TestCase
  include SlingTest

  def test_create_user
    m = uniqueness()
    u = create_user("testuser"+m)
    details = @um.get_user_props(u.name)
    assert_equal("testuser"+m, details["name"], "Expected username to match")
  end
 
  def test_create_group
    m = uniqueness()
    g = create_group("g-testgroup"+m)
    assert_not_nil(g,"Failed to create a group")
    assert_not_nil(g.name,"Failed to create a group, no name")
    details = @um.get_group_props(g.name)
    assert_equal("g-testgroup"+m, details["properties"]["name"], "Expected groupname to match")
  end
 
  def test_create_group_non_admin_user
    m = uniqueness()
    u = create_user("testuser"+m)
    assert_not_nil(u,"Failed to create a user")
    @s.switch_user(u)
    g = create_group("g-testgroup"+m)
    assert_not_nil(g,"Failed to create a group")
    assert_not_nil(g.name,"Failed to create a group, no name")
    details = @um.get_group_props(g.name)
    assert_equal("g-testgroup"+m, details["properties"]["name"], "Expected groupname to match")
  end

  def test_update_group
    m = uniqueness()
    g = create_group("g-testgroup"+m)
    member = create_user("memberofgroup"+m)
    assert_not_nil(g,"Failed to create a group")
    assert_not_nil(g.name,"Failed to create a group, no name")
    assert_not_nil(member,"Failed to create a group")
    assert_not_nil(member.name,"Failed to create a group, no name")
    details = @um.get_group_props(g.name)
    assert_equal("g-testgroup"+m, details["properties"]["name"], "Expected groupname to match")
    g.update_properties(@s,{ "sakai:group-title" => "GroupTitle" })
    details = @um.get_group_props(g.name)
    assert_equal("GroupTitle", details["properties"]["sakai:group-title"], "Could Not Set GroupTitle")
    assert_equal("g-testgroup"+m, details["properties"]["name"], "Expected groupname to match")
    g.update_properties(@s,{ "some-other-property" => "SomeOtherProperty" })
    details = @um.get_group_props(g.name)
    assert_equal("GroupTitle", details["properties"]["sakai:group-title"], "Group Tite should not have been reset")
    assert_equal("SomeOtherProperty", details["properties"]["some-other-property"], "Expected to be able to set some other property")
    assert_equal("g-testgroup"+m, details["properties"]["name"], "Expected groupname to match")
  
    g.add_member(@s,member,"user")
    details = @um.get_group_props(g.name)
    assert_equal("GroupTitle", details["properties"]["sakai:group-title"], "Group Tite should not have been reset")
    assert_equal("SomeOtherProperty", details["properties"]["some-other-property"], "Expected to be able to set some other property")
    assert_equal("g-testgroup"+m, details["properties"]["name"], "Expected groupname to match")
 end
 
  def test_group_deletion
    m = uniqueness()
    g = @um.create_group("g-testgroup"+m)
    assert_not_nil(g,"Failed to create a group")
    assert_not_nil(g.name,"Failed to create a group, no name")
    details = @um.get_group_props(g.name)
    assert_equal("g-testgroup"+m, details["properties"]["name"], "Expected groupname to match")
    @um.delete_group(g.name)
    res = @s.execute_get(@s.url_for(Group.url_for(g.name + ".json")))
    assert_equal("404", res.code, "Expected no group node")
  end
  
  def test_create_email_username
    m = uniqueness()
    u = create_user("testuser@gmail.com"+m)
  details = @um.get_user_props(u.name)
    assert_equal("testuser@gmail.com"+m, details["name"], "Expected username to match")
  end

end


