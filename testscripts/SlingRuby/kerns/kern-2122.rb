#!/usr/bin/env ruby

require 'nakamura/test'
require 'nakamura/file'
require 'nakamura/users'
require 'nakamura/full_group_creator'
require 'test/unit.rb'

include SlingUsers
include SlingFile

class TC_Kern2122 < Test::Unit::TestCase
  include SlingTest


  def setup
    super
    @fm = FileManager.new(@s)
    @um = UserManager.new(@s)
    @full_group_creator = SlingUsers::FullGroupCreator.new @s
    @full_group_creator.log.level = Logger::INFO
  end

  def test_add_and_remove_viewers
    m = Time.now.to_i.to_s

    # Create some users
    # manager = create_user("manager-#{m}")
    viewer1 = create_user("viewer1-#{m}")
    viewer2 = create_user("viewer2-#{m}")

    @s.switch_user(viewer1)
    ticks = Time.now.to_f

    managed_group = @full_group_creator.create_full_group viewer1.name, "group-#{m}", "kern-2122 int test group", "kern-2122 int test group description"
    assert_not_nil(managed_group, "expected managed group to be created")

    file_name = "random-#{ticks}.txt"
    file_body = "Add the time to make it sort of random #{Time.now.to_f}."
    upload_res = @fm.upload_pooled_file(file_name, file_body, 'text/plain')
    assert_equal("201",upload_res.code,"should have succeeded in uploading file #{file_name}")
    json = JSON.parse(upload_res.body)
    file_id = json[file_name]["poolId"]

    wait_for_indexer()
    find_res = @s.execute_get(@s.url_for("/var/search/pool/me/manager-all.tidy.json"))
    assert_equal("200",find_res.code,"should have succeeded in finding pool file #{file_id}")

    # add 3 viewers including the group that viewer1 manages
    add_viewers_res = @fm.manage_members(file_id, [viewer1.name, viewer2.name, managed_group.name], nil, nil, nil)
    assert_equal("200",add_viewers_res.code, "manage_members add viewers POST should have succeeded")
    members_res = @fm.get_members file_id
    json = JSON.parse(members_res.body)
    assert_equal("200",members_res.code, "get_members GET should have succeeded")
    viewers = json["viewers"]
    assert_equal(3, viewers.length, "should be 3 viewers added by viewer1" )

    # now have viewer1 try to remove viewer2
    remove_viewer2_res = @fm.manage_members(file_id, nil, [viewer2.name], nil, nil)
    members_res = @fm.get_members file_id
    json = JSON.parse(members_res.body)
    assert_equal("200",members_res.code)
    viewers = json["viewers"]
    assert_equal(3, viewers.length, "should be 3 viewers because viewer1 cannot remove viewer2" )

    # now have viewer1 remove managed group
    # this is how to remove an item from the library of a group managed by a user
    remove_group_res = @fm.manage_members(file_id, nil, [managed_group.name], nil, nil)
    assert_equal("200",remove_group_res.code, "expected viewer1 to be able to remove a group managed by them")
    members_res = @fm.get_members file_id
    assert_equal("200",members_res.code)
    json = JSON.parse(members_res.body)
    viewers = json["viewers"]
    # now have viewer1 remove themself
    # this is how to remove an item from a user's library
    remove_viewer1_res = @fm.manage_members(file_id, nil, [viewer1.name], nil, nil)
    assert_equal("200",remove_viewer1_res.code, "expected viewer1 to be able to remove themself")

    # need to switch to viewer2 to retrieve members as viewer1 cannot now do it having been removed
    @s.switch_user(viewer2)
    members_res = @fm.get_members file_id
    assert_equal("200",members_res.code)
    json = JSON.parse(members_res.body)
    viewers = json["viewers"]
    assert_equal(1, viewers.length, "should be 1 viewer left because viewer1 can remove themself from viewers" )
    remaining_viewer = viewers[0]
    assert_equal(viewer2.name, remaining_viewer["userid"], "should be viewer2 remaining after removal of viewer1" )
  end

end
