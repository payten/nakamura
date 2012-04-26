#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura'
require 'nakamura/test'
include SlingInterface
include SlingUsers

class TC_NodeCreateTest < Test::Unit::TestCase
  include SlingTest

  def test_create_node
    @log.info("test_create_node---------------------------------------------------START")
    testpath = "test/path"
    create_node(testpath, "a" => "foo", "b" => "bar")
    props = @s.get_node_props(testpath)
    assert_equal("foo", props["a"], "Expected property to be set")
    assert_equal("bar", props["b"], "Expected property to be set")
    @log.info("test_create_node---------------------------------------------------END")
  end

  def test_create_file_node
    @log.info("test_create_file_node----------------------------------------------START")
    filedata = "<html><head><title>fish</title></head><body><p>cat</p></body></html>"
    filepath = "test/filepath"
    create_file_node(filepath, "file", "file", filedata)
    res = @s.execute_get(@s.url_for(filepath + "/file"))
    assert_equal(200, res.code.to_i, "Expected GET to succeed")
    assert_equal(filedata, res.body, "Expected body back unmodified")
    @log.info("test_create_file_node----------------------------------------------END")
  end

  def test_create_file_node_and_version
    @log.info("test_create_file_node_and_version----------------------------------START")
    filedata = "<html><head><title>fish</title></head><body><p>cat</p></body></html>"
    filepath = "test/filepath"
    create_file_node(filepath, "file", "file", filedata)
    res = @s.execute_get(@s.url_for(filepath + "/file"))
    assert_equal(200, res.code.to_i, "Expected GET to succeed")
    assert_equal(filedata, res.body, "Expected body back unmodified")
	@log.info("Attempting version operation ")
    res = @s.execute_post(@s.url_for(filepath + "/file.save.html"))
    assert_equal(200, res.code.to_i, "Expected POST to save to succeed, looks like versioning is not working check the logs. "+res.body)
	@log.debug(res.body)
	
	@log.info("Attempting To List Versions ")
    res = @s.execute_get(@s.url_for(filepath + "/file.versions.json"))
    assert_equal(200, res.code.to_i, "Expected GET to versions to succeed, looks like versioning is not working check the logs. "+res.body)
	@log.debug(res.body)
	
    filedata = "<html><head><title>fishfingers</title></head><body><p>cat</p></body></html>"
    filepath = "test/filepath"
    create_file_node(filepath, "file", "file", filedata)
    res = @s.execute_get(@s.url_for(filepath + "/file"))
    assert_equal(200, res.code.to_i, "Expected GET to of second version succeed")
    assert_equal(filedata, res.body, "Expected body back unmodified")
    res = @s.execute_get(@s.url_for(filepath + "/file.versions.json"), "dummy_key" => "dummy")
    assert_equal(200, res.code.to_i, "Expected GET to versions to succeed, looks like versioning is not working check the logs. "+res.body)
	@log.debug(res.body)
    @log.info("test_create_file_node_and_version----------------------------------END")
  end
  
  def test_create_file_node_and_get_version_history
    @log.info("test_create_file_node_and_get_version_history----------------------START")
    filedata = "<html><head><title>fish</title></head><body><p>cat</p></body></html>"
    filepath = "test/filepath"
    create_file_node(filepath, "file", "file", filedata)
    res = @s.execute_get(@s.url_for(filepath + "/file"))
    assert_equal(200, res.code.to_i, "Expected GET to succeed")
    assert_equal(filedata, res.body, "Expected body back unmodified")
	@log.info("Attempting version history operation ")
    res = @s.execute_get(@s.url_for(filepath + "/file.versions.json"), "dummy_key" => "dummy")
    assert_equal(200, res.code.to_i, "Expected GET to versions to succeed, looks like versioning is not working check the logs. "+res.body)
    @log.info("test_create_file_node_and_get_version_history----------------------END")
  end

end


