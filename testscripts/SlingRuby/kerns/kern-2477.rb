#!/usr/bin/env ruby


require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers


class TC_Kern2477 < Test::Unit::TestCase
  include SlingTest

  def test_migrate_old_worlds
    @s.switch_user(User.admin_user())

    m = Time.now.to_nsec

    parentgroup = Group.new("g-test-#{m}")
    res = @s.execute_post(@s.url_for("#{$GROUP_URI}"), {
        ":name" => parentgroup.name,
        "_charset_" => "UTF-8",
        "sakai:group-title" => "Parent Group's Title",
        "sakai:roles" => "[
            {
                \"id\"=> \"member\",
                \"roleTitle\"=> \"MEMBERS\",
                \"title\"=> \"MEMBER\",
                \"allowManage\"=> false
            },
            {
                \"id\"=> \"manager\",
                \"roleTitle\"=> \"MANAGERS\",
                \"title\"=>\"MANAGER\",
                \"allowManage\"=>true
            }
        ]"
    })

    pseudogroup = Group.new("g-test-#{m}-manager")
    res = @s.execute_post(@s.url_for("#{$GROUP_URI}"), {
        ":name" => pseudogroup.name,
        "_charset_" => "UTF-8",
        "sakai:group-title" => "PseudoGroup Original Title",
        "sakai:pseudoGroup" => "true",
        "sakai:pseudogroupparent" => "#{parentgroup.name}"
    })
    res = @s.execute_get(@s.url_for("/system/userManager/group/#{pseudogroup.name}.tidy.json"))
    json = JSON.parse(res.body)
    assert_equal("true", json['properties']['sakai:pseudoGroup'])

    params = {
        "dryRun" => false,
        "reindexAll" => false,
        "limit" => 5000
    }

    # do the upgrade
    @s.execute_post("http://localhost:8080/system/sparseupgrade", params)

    # check the pseudogroup's after-upgrade data
    res = @s.execute_get(@s.url_for("/system/userManager/group/#{pseudogroup.name}.tidy.json"))
    json = JSON.parse(res.body)
    assert_equal(true, json['properties']['sakai:pseudoGroup'])
    assert_equal("Parent Group's Title", json['properties']['sakai:parent-group-title'])
    assert_equal("PseudoGroup Original Title", json['properties']['sakai:role-title'])
    assert_equal("MANAGERS", json['properties']['sakai:role-title-plural'])
    assert_nil(json['properties']['sakai:group-title'])

    # check the main group's after-upgrade data
    res = @s.execute_get(@s.url_for("/system/userManager/group/#{parentgroup.name}.tidy.json"))
    json = JSON.parse(res.body)

  end

end