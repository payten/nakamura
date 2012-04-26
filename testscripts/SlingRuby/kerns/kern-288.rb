#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'nakamura/search'
require 'nakamura/contacts'
require 'test/unit.rb'
include SlingContacts

class TC_Kern288Test < Test::Unit::TestCase
  include SlingTest

  def test_connection_details
    m = uniqueness()
    u1 = create_user("testuser#{m}")
    u2 = create_user("otheruser#{m}")
    cm = ContactManager.new(@s)
    @s.switch_user(u1)
    cm.invite_contact(u2.name, "follower")
    wait_for_indexer()
    pending = cm.get_pending
    assert(pending["results"].size == 1, "Expected pending invitation")
    res = cm.cancel_invitation(u2.name)
    assert_equal("200", res.code, "Expected cancel to succeed")
    wait_for_indexer()
    pending = cm.get_pending
    assert(pending["results"].size == 0, "Expected no pending invitation")
  end

end


