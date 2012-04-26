#!/usr/bin/env ruby


require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers


class TC_Kern2621 < Test::Unit::TestCase
  include SlingTest

  def test_fix_incomplete_tags
    @s.switch_user(User.admin_user())

    m = uniqueness()

    # create a fake tag missing its sakai:tag-name and sling:resourceType props
    res = @s.execute_post(@s.url_for("/tags/directory/foo#{m}/bar#{m}"), {
        ":name" => "bar",
        "_charset_" => "UTF-8"
    })

    params = {
        "dryRun" => false,
        "reindexAll" => false,
        "limit" => 5000
    }
    # do the upgrade
    @s.execute_post("http://localhost:8080/system/sparseupgrade", params)

    # check the tag's after-upgrade data
    res = @s.execute_get(@s.url_for("/tags/directory/foo#{m}/bar#{m}.tidy.json"))
    json = JSON.parse(res.body)
    assert_equal("directory/foo#{m}/bar#{m}", json['sakai:tag-name'])
    assert_equal("sakai/tag", json['sling:resourceType'])

    # check the parent tag
    res = @s.execute_get(@s.url_for("/tags/directory/foo#{m}.tidy.json"))
    json = JSON.parse(res.body)
    assert_equal("directory/foo#{m}", json['sakai:tag-name'])
    assert_equal("sakai/tag", json['sling:resourceType'])


  end

end