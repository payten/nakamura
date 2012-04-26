#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers


class TC_Kern2277 < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @m = uniqueness()

    @owner = create_test_user("owner")
    @editor = create_test_user("editor")

    @s.switch_user(@owner)

  end

  def test_editor_permissions

    # make pooled content item
    result = @s.execute_post(@s.url_for("/system/pool/createfile"), {
        "sakai:pooled-content-file-name" => "file" + @m,
        "sakai:description" => "desc",
        "mimeType" => "x-sakai/document"
    })
    assert_equal(201, result.code.to_i)

    pool_id = JSON.parse(result.body)["_contentItem"]["poolId"]
    path = @s.url_for("/p/" + pool_id)

    # assign @editor as an editor of pooled content
    result = @s.execute_post(path + ".members.html", {
        ":manager" => [@owner.name],
        ":editor" => [@editor.name]
    })
    assert_equal(200, result.code.to_i)

    # make sure @editor shows up in editors list
    @log.info("Created a pooled content item at " + path)
    result = @s.execute_get(path + ".members.json")
    json = JSON.parse(result.body)
    assert_equal(@editor.name, json["editors"][0]["userid"])

    # check the ACLs for @editor
    result = @s.execute_get(path + ".acl.json")
    json = JSON.parse(result.body)
    assert_includes(json[@editor.name]["granted"], "Read")
    assert_includes(json[@editor.name]["granted"], "Write")
    assert_not_includes(json[@editor.name]["granted"], "Delete")
    assert_not_includes(json[@editor.name]["granted"], "Read ACL")
    assert_not_includes(json[@editor.name]["granted"], "Write ACL")
    assert_not_includes(json[@editor.name]["granted"], "Delete ACL")
    assert_not_includes(json[@editor.name]["granted"], "Manage")
    assert_not_includes(json[@editor.name]["granted"], "All")

    # and check a couple of owner's ACLs just to be sure
    assert_includes(json[@owner.name]["granted"], "Read ACL")
    assert_includes(json[@owner.name]["granted"], "Manage")

    # make sure editor can't delete the item
    @s.switch_user(@editor)
    result = @s.execute_post(path, {
        ":operation"=>"delete"
    })
    assert_equal(403, result.code.to_i)

    @s.switch_user(@owner)
    # now remove the editor's editorship
    result = @s.execute_post(path + ".members.html", {
        ":manager" => [@owner.name],
        ":editor@Delete" => [@editor.name]
    })
    assert_equal(200, result.code.to_i)

    # make sure @editor NO LONGER shows up in editors list
    result = @s.execute_get(path + ".members.json")
    json = JSON.parse(result.body)
    assert_equal(0, json["editors"].length)

    # and make sure @editor is no longer in ACLs
    result = @s.execute_get(path + ".acl.json")
    json = JSON.parse(result.body)
    assert_equal(0, json[@editor.name]["granted"].length)

  end

end
