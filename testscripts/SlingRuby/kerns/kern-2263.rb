#!/usr/bin/env ruby


require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers


class TC_Kern2263 < Test::Unit::TestCase
  include SlingTest

  def test_group_is_really_deleted
      m = Time.now.to_nsec
      
      @s.switch_user(User.admin_user())
      
      # create a group
      group = Group.new("g-test-#{m}")
      @s.switch_user(User.admin_user())
      res = @s.execute_post(@s.url_for("#{$GROUP_URI}"), {
        ":name" => group.name,
        "_charset_" => "UTF-8"
      })
      
      # confirm that we can read the group and the group home
      res = @s.execute_get(@s.url_for("/system/userManager/group/#{group.name}.tidy.json"))
      assert_equal("200", res.code, "New group content is missing")
      res = @s.execute_get(@s.url_for("/~#{group.name}.tidy.json"))
      assert_equal("200", res.code, "New group home is missing")
      
      # delete the group
      @s.execute_post(@s.url_for("/system/userManager/group/#{group.name}.delete.html"), {
        ":status" => "standard",
        ":applyTo" => "#{group.name}"
      })
      
      # confirm that the group and the group home are gone
      res = @s.execute_get(@s.url_for("/system/userManager/group/#{group.name}.tidy.json"))
      assert_equal("404", res.code, "This group should be gone.")
      res = @s.execute_get(@s.url_for("/~#{group.name}.tidy.json"))
      assert_equal("404", res.code, "New group home should be gone")
      
    end
    
    def test_group_in_me_feed
      m = Time.now.to_nsec
      
      @s.switch_user(User.admin_user())
      
      # create a user
      manager = create_user("user-manager-#{m}")
      
      # create a group
      @s.switch_user(manager)
      group = Group.new("g-test-#{m}")
      res = @s.execute_post(@s.url_for("#{$GROUP_URI}"), {
        ":name" => group.name,
        "_charset_" => "UTF-8"
      })
      
      # add the user as a manager.
      res = @s.execute_post(@s.url_for("/system/userManager/group/#{group.name}.update.html"), {
        ":manager" => manager.name,
        ":member" => manager.name
      })
      
      # check the contents of their me feed
      res = @s.execute_get(@s.url_for("/system/me"))
      me = JSON.parse(res.body)
      #assert_equal("200", res.code, "Should have been able to get /system/me")
      groups = me["groups"]
      assert_equal(1, groups.size, "Should have one groups in summary #{group.name}")
      assert_not_nil(groups.find{|e| e["groupid"] == group.name}, "Manager should be a member of the group #{res.body} #{group.name}")
      
      # delete the group
      @s.switch_user(User.admin_user())
      @s.execute_post(@s.url_for("/system/userManager/group/#{group.name}.delete.html"), {
        ":status" => "standard"
      })
      
      # check /system/me again
      @s.switch_user(manager)
      res = @s.execute_get(@s.url_for("/system/me"))
      me = JSON.parse(res.body)
      assert_equal("200", res.code, "Should have been able to get /system/me")
      groups = me["groups"]
      assert_equal(0, groups.size, "Having deleted #{group.name} it should not be in the me feed")
    end
  
  def test_pseudo_group_delete
    m = Time.now.to_nsec
    
    @s.switch_user(User.admin_user())
    
    # create a user
    manager = create_user("user-manager-#{m}")
    
    forbidden_group = Group.new("g-forbidden-#{m}")
    res = @s.execute_post(@s.url_for("#{$GROUP_URI}"), {
      ":name" => forbidden_group.name,
      "_charset_" => "UTF-8"
    })
  
    group = Group.new("g-test-#{m}")
    res = @s.execute_post(@s.url_for("#{$GROUP_URI}"), {
      ":name" => group.name,
      "_charset_" => "UTF-8"
    })
    
    manager_group = Group.new("g-test-manager-#{m}")
    res = @s.execute_post(@s.url_for("#{$GROUP_URI}"), {
      ":name" => manager_group.name,
      "_charset_" => "UTF-8"
    })
  
      manager_groupB = Group.new("g-test-managerB-#{m}")
      res = @s.execute_post(@s.url_for("#{$GROUP_URI}"), {
        ":name" => manager_groupB.name,
        "_charset_" => "UTF-8"
      })
    
    # add manager_group and manaber_groupB as managers.
    res = @s.execute_post(@s.url_for("/system/userManager/group/#{group.name}.update.html"), {
      ":manager" => manager_group.name,
      ":member" => manager_group.name,
      ":manager" => manager_groupB.name,
      ":member" => manager_groupB.name
    })
    
    # add the manager as manager of pseudo-group.
    res = @s.execute_post(@s.url_for("/system/userManager/group/#{manager_group.name}.update.html"), {
      ":manager" => manager.name,
      ":member" => manager.name
    })
    
    # add the manager as manager of pseudo-group.
    res = @s.execute_post(@s.url_for("/system/userManager/group/#{manager_groupB.name}.update.html"), {
      ":manager" => manager.name,
      ":member" => manager.name
    })
    
    # check delete permission
    @s.switch_user(manager)
    res = @s.execute_post(@s.url_for("/system/userManager/group/#{manager_group.name}.delete.html"), {
      ":status" => "standard",
      ":applyTo" => "#{manager_group.name}",
      ":applyTo" => "#{manager_groupB.name}"
    })
    assert_equal("200", res.code, "Should have been allowed to delete this group.")
    
    # check delete permission
   res = @s.execute_post(@s.url_for("/system/userManager/group/#{group.name}.delete.html"), {
      ":status" => "standard"
   })
   assert_equal("200", res.code, "Should have been allowed to delete this group.")
    
   res = @s.execute_post(@s.url_for("/system/userManager/group/#{forbidden_group.name}.delete.html"), {
        ":status" => "standard"
   })
   assert_equal("403", res.code, "Should not have been allowed to delete this group.")
  end
    
  
end