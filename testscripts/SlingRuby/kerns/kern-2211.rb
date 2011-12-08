#!/usr/bin/env ruby

require 'nakamura/test'
require 'nakamura/users'
require 'test/unit.rb'

include SlingUsers

class TC_Kern2211 < Test::Unit::TestCase
  include SlingTest


  def setup
    super
    @um = UserManager.new(@s)
  end
  
  def test_user_not_exist
    m = Time.now.to_f.to_s.gsub('.', '')
    data = {}
    userid = "lower_case_user.#{m}"
    data["userid"] = userid
    response = @s.execute_get(@s.url_for( "/system/userManager/user.exists.html"), data)
    assert_equal("404", response.code)
    assert_equal("Not Found", response.message)
  end
    
  def test_create_user_and_user_exists  
    m = Time.now.to_f.to_s.gsub('.', '')
    lower_case_user_name = "lower_case_user.#{m}"
    data = {}
    data[":name"] = lower_case_user_name
    data["pwd"] = "testuser"
    data["pwdConfirm"] = "testuser"

    response = @s.execute_post(@s.url_for( "/system/userManager/user.create.json"), data)
    response_json = JSON.parse response.body
    assert_equal(201, response_json["status.code"])
    assert_equal("user #{lower_case_user_name} created", response_json["status.message"])
    
    wait_for_indexer
    
    # now upper case the user name and try again
    upcase_user_name = lower_case_user_name.upcase
    data[":name"] = upcase_user_name
    data["pwd"] = "testuser"
    data["pwdConfirm"] = "testuser"
    response = @s.execute_post(@s.url_for( "/system/userManager/user.create.json"), data)
    response_json = JSON.parse response.body
    assert_equal(400, response_json["status.code"])
    # because the solr search for user by name is case insensitive
    assert_equal("User with name #{upcase_user_name} already exists", response_json["status.message"])
    
    # now check to be sure user created exists in the LiteUserExistsServlet
    userid = lower_case_user_name
    data["userid"] = userid
    # LiteUserExistsServlet doesn't return json
    response = @s.execute_get(@s.url_for( "/system/userManager/user.exists.html"), data)
    assert_equal("204", response.code)
    assert_equal("No Content", response.message)
    
    # now check to be sure upper cased version of user exists in the LiteUserExistsServlet
    userid = upcase_user_name
    data["userid"] = userid
    # LiteUserExistsServlet doesn't return json
    response = @s.execute_get(@s.url_for( "/system/userManager/user.exists.html"), data)
    assert_equal("204", response.code)
    assert_equal("No Content", response.message)
  end
end
