#!/usr/bin/env ruby


require 'nakamura/test'
require 'nakamura/file'
require 'nakamura/users'
require 'test/unit.rb'
include SlingUsers
include SlingFile

class TC_Kern1836Test < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @fm = FileManager.new(@s)
    @um = UserManager.new(@s)
  end

  def test_delete_operation_recurses
    m = Time.now.to_nsec
    @s.switch_user(User.admin_user())
    homefolder = User.admin_user().home_path_for(@s)

    # create a parent dir
    parentpath = @s.url_for("#{homefolder}/#{m}/parent")
    res = @s.execute_post("#{parentpath}", {"sling:resourceType" => "application/json"})
    assert_equal("201", res.code, " #{parentpath} was not created")

    res = @s.execute_get("#{parentpath}.json")
    assert_equal("200", res.code, " #{parentpath} cannot be retrieved")

    # create a child dir
    childpath = @s.url_for("#{homefolder}/#{m}/parent/child")
    res = @s.execute_post(childpath, {"sling:resourceType" => "application/json"})
    assert_equal("201", res.code, " #{childpath} was not created")

    # make sure child exists
    res = @s.execute_get("#{childpath}.json")
    assert_equal("200", res.code, " #{childpath} cannot be retrieved")

    # delete the parent
    res = @s.execute_post(parentpath, {":operation" => "delete"})
    assert_equal(200, res.code.to_i, "Expected to be able to delete the parent.")

    # make sure child is also deleted
    res = @s.execute_get("#{childpath}.json")
    assert_equal("404", res.code, " #{childpath} was not deleted")
  end
end
