#!/usr/bin/env ruby


require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers


class TC_Kern2560 < Test::Unit::TestCase
  include SlingTest

  def test_fix_world_template_ids
    @s.switch_user(User.admin_user())

    m = uniqueness()

    group = Group.new("g-test-#{m}")
    res = @s.execute_post(@s.url_for("#{$GROUP_URI}"), {
        ":name" => group.name,
        "_charset_" => "UTF-8",
        "sakai:group-title" => "A Title",
        "sakai:templateid" => "simplegroup"
    })

    params = {
        "dryRun" => false,
        "reindexAll" => false,
        "limit" => 5000
    }
    # do the upgrade
    @s.execute_post("http://localhost:8080/system/sparseupgrade", params)

    # check the simplegroup's after-upgrade data
    res = @s.execute_get(@s.url_for("/system/userManager/group/#{group.name}.tidy.json"))
    json = JSON.parse(res.body)
    assert_equal("simple-group", json['properties']['sakai:templateid'])

  end

end