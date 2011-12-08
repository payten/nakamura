#!/usr/bin/env ruby

require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers


class TC_Kern2416 < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @m = Time.now.to_f.to_s.gsub('.', '')

    @alice = create_test_user("alice")

    @bob = create_test_user("bob")

    @ace_service = @s.url_for("/system/userManager/user/" + @alice.name + ".modifyAce.html")

    @alice_authz_url = @s.url_for("/system/userManager/user/" + @alice.name + ".json")

  end

  def test_bad_posts
    result = @s.execute_post(@ace_service, {})
    assert_equal(500, result.code.to_i)

    result = @s.execute_post(@ace_service, {
        "privilege@jcr:read" => "granted"
    })
    assert_equal(500, result.code.to_i)

    result = @s.execute_post(@s.url_for("/system/userManager/user/supplyingnonexistentuseridcauses500error.modifyAce.html"), {})
    assert_equal(500, result.code.to_i)
  end

  def test_read_privilege

    # deny everyone read
    @s.switch_user(@alice)
    result = @s.execute_post(@ace_service, {
        "principalId" => "everyone",
        "privilege@jcr:read" => "denied"
    })
    assert_equal(200, result.code.to_i)
    result = @s.execute_post(@ace_service, {
        "principalId" => "anonymous",
        "privilege@jcr:read" => "denied"
    })
    assert_equal(200, result.code.to_i)

    # make sure bob cannot read
    @s.switch_user(@bob)
    result = @s.execute_get(@alice_authz_url)
    assert_equal(404, result.code.to_i)

    # give bob individual read access
    @s.switch_user(@alice)
    result = @s.execute_post(@ace_service, {
        "principalId" => @bob.name,
        "privilege@jcr:read" => "granted"
    })
    assert_equal(200, result.code.to_i)

    # make sure bob can read
    @s.switch_user(@bob)
    result = @s.execute_get(@alice_authz_url)
    assert_equal(200, result.code.to_i)

    # now take bob's read access away
    @s.switch_user(@alice)
    result = @s.execute_post(@ace_service, {
        "principalId" => @bob.name,
        "privilege@jcr:read" => "denied"
    })
    assert_equal(200, result.code.to_i)

    # make sure bob can no longer read
    @s.switch_user(@bob)
    result = @s.execute_get(@alice_authz_url)
    assert_equal(404, result.code.to_i)

    # now clear bob's permissions
    @s.switch_user(@alice)
    result = @s.execute_post(@ace_service, {
        "principalId" => @bob.name,
        "privilege@jcr:read" => "none"
    })
    assert_equal(200, result.code.to_i)

    # make sure bob can still not read
    @s.switch_user(@bob)
    result = @s.execute_get(@alice_authz_url)
    assert_equal(404, result.code.to_i)

    # now grant everyone read
    @s.switch_user(@alice)
    result = @s.execute_post(@ace_service, {
        "principalId" => "everyone",
        "privilege@jcr:read" => "granted"
    })
    assert_equal(200, result.code.to_i)

    # now bob should be able to read again because he's part of everyone
    @s.switch_user(@bob)
    result = @s.execute_get(@alice_authz_url)
    assert_equal(200, result.code.to_i)

    # but anonymous should not be able to read
    @s.switch_user(User.anonymous)
    result = @s.execute_get(@alice_authz_url)
    assert_equal(404, result.code.to_i)

    # now grant anonymous read
    @s.switch_user(@alice)
    result = @s.execute_post(@ace_service, {
        "principalId" => "anonymous",
        "privilege@jcr:read" => "granted"
    })
    assert_equal(200, result.code.to_i)

    # and now anonymous should be able to read
    @s.switch_user(User.anonymous)
    result = @s.execute_get(@alice_authz_url)
    assert_equal(200, result.code.to_i)

  end

end
