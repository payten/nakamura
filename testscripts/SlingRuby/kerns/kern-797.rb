#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura'
require 'nakamura/test'
require 'nakamura/file'
require 'nakamura/message'
require 'test/unit.rb'
include SlingInterface
include SlingUsers
include SlingMessage
include SlingFile

class TC_MyFileTest_797 < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @ff = FileManager.new(@s)
  end

  def test_canModify
    m = uniqueness()
    @siteid = "creator#{m}";
    creator = create_user("#{@siteid}")

    # Upload a file to the user's public space.
    @s.switch_user(creator)
    resp = @s.execute_file_post(@s.url_for("/system/pool/createfile"), "alfa", "alfa", "This is some random content: alfaalfa.", "text/plain")
    assert_equal(201, resp.code.to_i(), "Expected to be able to upload a file.")
    uploadresult = JSON.parse(resp.body)
    alfa = uploadresult['alfa']
    assert_not_nil(alfa) 
    poolId = alfa['poolId']   
    
    # Check canModify on uploaded file

    # terse output
    url = "/p/#{poolId}.canModify.json";
    resp = @s.execute_get(@s.url_for(url));
    assert_equal(200, resp.code.to_i, "Should be OK");
    json = JSON.parse(resp.body)
    # creator canModify own file
    assert_equal(true, json["/p/#{poolId}"])
    assert_equal(nil, json["privileges"])

    # verbose output
    url = "/p/#{poolId}.canModify.json?verbose=true";
    resp = @s.execute_get(@s.url_for(url));
    assert_equal(200, resp.code.to_i, "Should be OK");
    @log.info(resp.body)
    json = JSON.parse(resp.body)
    # creator canModify own file
    assert_equal(true, json["/p/#{poolId}"])
    assert_not_nil(json["privileges"])
    assert_equal(true, json["privileges"]["All"])

    # Check canModify on /var

    # terse output
    url = "/var.canModify.json";
    resp = @s.execute_get(@s.url_for(url));
    assert_equal(200, resp.code.to_i, "Should be OK");
    json = JSON.parse(resp.body)
    # creator cannot modify /var
    assert_equal(false, json["/var"])
    assert_equal(nil, json["privileges"])

    # verbose output
    url = "/var.canModify.json?verbose=true";
    resp = @s.execute_get(@s.url_for(url));
    assert_equal(200, resp.code.to_i, "Should be OK");
    json = JSON.parse(resp.body)
    # creator cannot modify /var
    assert_equal(false, json["/var"])
    assert_not_nil(json["privileges"])
    assert_equal(true, json["privileges"]["jcr:read"])

    # verbose output
    @s.switch_user(SlingUsers::User.admin_user())
    
    # verify 404 not found
    url = "/p/foo.canModify.json?verbose=true";
    resp = @s.execute_get(@s.url_for(url));
    assert_equal(404, resp.code.to_i, "Should be not found");
    
  end

  def teardown
    super
  end

end

