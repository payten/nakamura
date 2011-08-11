#!/usr/bin/env ruby

# Add all files in testscripts\SlingRuby\lib directory to ruby "require" search path
require './ruby-lib-dir.rb'

require 'sling/test'
require 'sling/file'
require 'sling/users'
require 'test/unit.rb'
include SlingUsers
include SlingFile

class TC_Kern1045 < Test::Unit::TestCase
  include SlingTest


  def setup
    super
    @fm = FileManager.new(@s)
    @um = UserManager.new(@s)
  end


  def test_search_me
    m = Time.now.to_i.to_s

    # Create some users
    owner = create_user("creator2-#{m}")
    viewer = create_user("manager2-#{m}")
    groupuser = create_user("groupuser2-#{m}")

    @s.switch_user(owner)
    content = Time.now.to_f
    name = "random-#{content}.txt"
    fileBody = "Add the time to make it sort of random #{Time.now.to_f}."
    res = @fm.upload_pooled_file(name, fileBody, 'text/plain')
    json = JSON.parse(res.body)
    id = json[name]['poolId']


    # Search the files that I manage .. should be 1
    res = @s.execute_get(@s.url_for("/var/search/pool/me/manager-all.tidy.json"))
    assert_equal("200",res.code,res.body)
    json = JSON.parse(res.body)
    assert_equal(1, json["results"].length)
    assert_equal(fileBody.length ,json["results"][0]["_length"])

  end

end
