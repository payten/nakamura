#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura'
require 'nakamura/test'
require 'nakamura/contacts'
require 'test/unit.rb'
include SlingInterface
include SlingUsers
include SlingContacts

class TC_Kern280Test < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @cm = ContactManager.new(@s)
  end


  def test_connect_non_existant_user
    m = uniqueness()
    u = create_user("testuser"+m)
    @s.switch_user(u)
    res = @cm.invite_contact("nonexisant_user"+m, [ "coworker", "friend" ])
    assert_equal("404", res.code, "User should not have existed")
  end

end


