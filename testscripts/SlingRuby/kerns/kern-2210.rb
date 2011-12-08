#!/usr/bin/env ruby

require 'nakamura/test'
require 'nakamura/contacts'
require 'nakamura/file'
require 'nakamura/users'
include SlingUsers
include SlingContacts

class TC_Kern2210Test < Test::Unit::TestCase
  include SlingTest
  
  def setup
    super
    @um = SlingUsers::UserManager.new @s
    @cm = SlingContacts::ContactManager.new @s
    m = Time.now.to_f.to_s.gsub('.', '')
    @user1 = create_user "test-user1-#{m}"
    @group = create_group "g-test-group-#{m}"
    sleep 0.1
    m = Time.now.to_f.to_s.gsub('.', '')
    @user2 = create_user "test-user2-#{m}"

    response = @group.add_members @s, [@user1.name, @user2.name]
    assert_equal("200", response.code, "expected adding members to group to succeed")
    @s.switch_user @user1
    response = @cm.invite_contact(@user2.name, ["Colleague"])
    assert_equal("200", response.code, "expected user1 inviting user2 to succeed")
    
    @s.switch_user @user2
    response = @cm.accept_contact(@user1.name)
    assert_equal("200", response.code, "expected user2 accepting invitation to succeed")
     
    wait_for_indexer
    @s.switch_user @user1
  end
  
  def teardown
    super
  end
  
  def test_group_members_feed
    data = {}
    data["group"] = @group.name
    data["items"] = 2
    data["page"] = 0
    response = @s.execute_get(@s.url_for( "/var/search/groupmembers-all.json"), data)
    assert_equal("200", response.code)
    response_json = JSON response.body
    results = response_json["results"]
    invited_user_found = false
    results.each do |result|
      if (result["userid"].eql? @user2.name)
        invited_user_found = true
        connection_state = result["sakai:state"]
        assert_equal("ACCEPTED", connection_state, "expected @user2 to have accepted invitation")
        connection_types = result["sakai:types"]
        assert_equal(["Colleague"], connection_types, "expected conntection types to be ['Colleague']")
      end
    end
    assert_equal(true, invited_user_found, "Expected to find @user2 in feed")
  end
  
  def test_users_feed  
    data = {}
    data["q"] = @user2.name
    data["items"] = 10
    data["page"] = 0
    response = @s.execute_get(@s.url_for( "/var/search/users.json"), data)
    assert_equal("200", response.code)
    response_json = JSON response.body
    results = response_json["results"]
    invited_user_found = false
    results.each do |result|
      if (result["userid"].eql? @user2.name)
        invited_user_found = true
        connection_state = result["sakai:state"]
        assert_equal("ACCEPTED", connection_state, "expected @user2 to have accepted invitation")
        connection_types = result["sakai:types"]
        assert_equal(["Colleague"], connection_types, "expected conntection types to be ['Colleague']")
      end
    end
    assert_equal(true, invited_user_found, "Expected to find @user2 in feed")
  end
  
  def test_general_feed
    # http://localhost:8080/var/search/general.json?page=0&items=10&q=king&sortOn=_lastModified&sortOrder=desc&_charset_=utf-8
    data = {}
    data["q"] = @user2.name
    response = @s.execute_get(@s.url_for( "/var/search/general.json"), data)
    assert_equal("200", response.code)
    response_json = JSON response.body
    results = response_json["results"]
    invited_user_found = false
    results.each do |result|
      if (result["userid"].eql? @user2.name)
        invited_user_found = true
        connection_state = result["sakai:state"]
        assert_equal("ACCEPTED", connection_state, "expected @user2 to have accepted invitation")
        connection_types = result["sakai:types"]
        assert_equal(["Colleague"], connection_types, "expected conntection types to be ['Colleague']")
      end
    end
    assert_equal(true, invited_user_found, "Expected to find @user2 in feed")
  end
  
  def test_auth_profile_feed
    response = @s.execute_get(@s.url_for("~#{@user2.name}/public/authprofile.profile.json"))
    assert_equal("200", response.code)
    response_json = JSON response.body
    connection_state = response_json["sakai:state"]
    assert_equal("ACCEPTED", connection_state, "expected @user2 to have accepted invitation")
    connection_types = response_json["sakai:types"]
    assert_equal(["Colleague"], connection_types, "expected conntection types to be ['Colleague']")
  end
  
end
