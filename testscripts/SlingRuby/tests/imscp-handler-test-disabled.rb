#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'nakamura/file'
require 'nakamura/message'
require 'test/unit.rb'
include SlingInterface
include SlingUsers
include SlingMessage
include SlingFile

class TC_IMSCPFileHandlerTest < Test::Unit::TestCase
  include SlingTest
  TEST_SAKAI_DOC_MIME_TYPE = "x-sakai/document"

  def setup
    super
    @ff = FileManager.new(@s)
  end

  def test_import_course
    m = uniqueness()
    @user = create_user("test_user" + m)
    @s.switch_user(@user)
    content = open('../dataload/content-packaging.zip', 'rb') { |f| f.read }
    res = @s.execute_file_post(@s.url_for("/system/pool/createfile"), "kaka.zip", "kaka.zip", content, "application/zip")
    assert_equal(201, res.code.to_i(), "Expected to be able to upload a zip file, which contains imsmanifest.xml")
    json = JSON.parse(res.body)
    assert_not_nil(json, "Expecting json from course import")
    content = json["kaka.zip"]["item"]
    mime_type = content["sakai:custom-mimetype"]
    assert_equal(TEST_SAKAI_DOC_MIME_TYPE, mime_type, "Expecting sakai document type from response")
    structure = JSON.parse(content['structure0'])
    # Check whether resources element contains the file information
    recursive_assert(structure, content);
  end

  def recursive_assert(structure, content)
    structure.each do |item|
      s = item[0].to_s
      if s[0,1].to_s !="_" then
        resourceId = structure[s]['_ref'].to_s
        if resourceId != nil && resourceId.length > 0 then
          assert_not_nil(content['resources'][resourceId], "Expected there is resource information")
        end
        recursive_assert(structure[s], content)
      end
    end
  end
end
