#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'nakamura/file'
require 'nakamura/users'
require 'test/unit.rb'
include SlingUsers

class TC_Kern2034Test < Test::Unit::TestCase
  include SlingTest

  def test_explict_anonymous_viewer
    m = uniqueness()
    @s.switch_user(User.admin_user)
    groupid = "testgroup-#{m}"
    group = create_group(groupid)
    res = group.remove_viewer(@s, "anonymous")
    assert_equal("200", res.code, "Not able to add everyone as viewer")
    groupurl = @s.url_for(Group.url_for(groupid))
    @s.switch_user(User.anonymous)
    res = @s.execute_get("#{groupurl}.json")
    assert_not_equal("200", res.code, "The group should not be public")
    @s.switch_user(User.admin_user)
    res = group.add_viewer(@s, "anonymous")
    assert_equal("200", res.code, "Not able to add anonymous as viewer")
    @s.switch_user(User.anonymous)
    res = @s.execute_get("#{groupurl}.json")
    assert_equal("200", res.code, "The group should now be public")
  end
end
