#!/usr/bin/env ruby


require 'nakamura/test'
require 'nakamura/file'
require 'nakamura/users'
require 'nakamura/full_group_creator'
include SlingUsers


class TC_Kern2205Test < Test::Unit::TestCase
  include SlingTest
  def setup
    super
    @um = SlingUsers::FullGroupCreator.new(@s, nil)
  end
  
  def teardown
    super
  end
  
  def test_add_pseudo_group_to_itself
    m = Time.now.to_f.to_s.gsub('.', '')
    user = create_user "testuser-#{m}"
    
    group = @um.create_full_group user.name, "testgroup-#{m}"
    group_details = group.details(@s)
    group_members_count = group_details["properties"]["membersCount"]
    assert_equal(2, group_members_count, "new simplegroups has 2 members")
    # switch to admin user to override sakai:group-joinable
    # constraint in LiteAbstractSakaiGroupPostServlet
    @s.switch_user User.admin_user
    # add main group to itself to check for membersCount
    # in non-recursive code in GroupMembersCounter.java
    group.add_member @s, group.name, "g"
    wait_for_indexer()
    group_details = group.details(@s)
    group_members_count = group_details["properties"]["membersCount"]
    assert_equal(3, group_members_count, "adding main group to itself group should now have 3 members")

    members_pseudo_group = Group.new "#{group.name}-member"
    members_pseudo_group_details = members_pseudo_group.details @s
    members_pseudo_group_members_count = members_pseudo_group_details["properties"]["membersCount"]
    assert_equal(0, members_pseudo_group_members_count, "new members pseudo goup has 0 members")
    
#    now add the pseudo_group to iteself as a member to test recursive code in GroupMembersCounter.java
    members_pseudo_group.add_member @s, members_pseudo_group.name, "g"
    wait_for_indexer()
    members_pseudo_group_details = members_pseudo_group.details @s
    members_pseudo_group_members_count = members_pseudo_group_details["properties"]["membersCount"]
    assert_equal(1, members_pseudo_group_members_count, "after adding members_pseudo_group to itself, members count should be 1")
  end
end
