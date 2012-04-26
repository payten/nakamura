#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura'
require 'nakamura/test'
require 'nakamura/file'
require 'nakamura/message'
require 'test/unit.rb'
include SlingInterface
include SlingUsers
include SlingMessage
include SlingFile

class TC_MyFileTest_891 < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @ff = FileManager.new(@s)
  end

  def test_upload_file
    m = uniqueness()
    @log.info("Creating user simon"+m)
    simon = create_user("simon"+m)

    @s.switch_user(simon)
    publicSimon = simon.public_path_for(@s)

    # Upload a couple of files to the user his public space.
    res = @s.execute_file_post(@s.url_for("/system/pool/createfile"), "alfa", "alfa", "This is some random content: alfaalfa.", "text/plain")
    assert_equal(201, res.code.to_i(), "Expected to be able to upload a file.")
    uploadresult = JSON.parse(res.body)
    alfa = uploadresult['alfa']
    assert_not_nil(alfa)
    alphaID = alfa['poolId']

    res = @s.execute_file_post(@s.url_for("/system/pool/createfile"), "beta", "beta", "This is some random content: betabeta.", "text/plain")
    # This will return modified..
    assert_equal(201, res.code.to_i(), "Expected to be able to upload a file.")
    uploadresult = JSON.parse(res.body)
	  beta = uploadresult['beta']
    assert_not_nil(beta)
    betaID = beta['poolId']

    # Tag the alfa file.
    res = @ff.tag("/p/#{alphaID}", "/tags/footag#{m}")
    assert_equal(200, res.code.to_i(), "Expected to be able to tag an uploaded file.")

    # Get tag info
    res = @s.execute_get(@s.url_for("/tags/footag#{m}.json"))
    tag = JSON.parse(res.body)
    assert_not_nil(tag, "No response when creating a tag.")

    #Try uploading as anonymous
    @log.info("Check that Anon is denied ")
    @s.switch_user(SlingUsers::User.anonymous)
    res = @s.execute_file_post(@s.url_for("/system/pool/createfile"), "anon", "anon", "This is some random content: anonanon.", "text/plain")
    if ( res.code == "200" )
      assert_equal("-1",res.code,"Expected not be be able to upload a file as anon user "+res.body)
    end

    res = @ff.myfiles("*")
    myfiles = JSON.parse(res.body)
    assert_equal(0, myfiles["total"].to_i(), "Expected 0 files for anon.")

  end

  def teardown
    @created_users.each do |user|
      #@s.debug = true
      @s.switch_user(user)
      #@s.debug = false
    end

    super
  end

end

