#!/usr/bin/env ruby
# encoding: UTF-8

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura'
require 'nakamura/test'
require 'nakamura/authz'
require 'test/unit.rb'
include SlingInterface
include SlingUsers
include SlingAuthz


class TC_KernMeTest < Test::Unit::TestCase
  include SlingTest
  
  def set_first_name(name, user)
    public = user.public_path_for(@s)
    path = "#{public}/authprofile/basic.profile.json"
    res = @s.execute_post(@s.url_for(path), {
          ":content" => "{\"elements\":{\"firstName\":{\"value\":\"#{name}\"},\"lastName\":{\"value\":\"Sixpack\"},\"email\":{\"value\":\"somebody@example.com\"}}}",
          ":contentType" => "json",
          ":operation" => "import",
          ":removeTree" => "true",
          ":replace" => "true",
          ":replaceProperties" => "true",
          "_charset_" => "UTF-8"
        })
    @log.info(res.body)
  end
  
  def get_system_me
    res = @s.execute_get(@s.url_for("/system/me"))
    @log.info(res.body)
    return JSON.parse(res.body)
  end
  
  
  def test_me_service
    m = uniqueness()
    userid = "testuser-#{m}"
    user = create_user(userid)
    
    @s.switch_user(user)
    
    # Safe characters
    characters = "foobar"
    set_first_name(characters, user)
    json = get_system_me()
  
    
    # Check if name is correct.
    assert_equal(characters, json["profile"]["basic"]["elements"]["firstName"]["value"], "Safe characters didn't match")
    
    # Non-safe
    characters = "ççççç"
    set_first_name(characters, user)
    json = get_system_me()
    
    # Check if name is correct.
    assert_equal(characters, json["profile"]["basic"]["elements"]["firstName"]["value"], "Unsafe characts didn't match")
  end
  
end

