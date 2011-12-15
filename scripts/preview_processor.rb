#!/usr/bin/env ruby
require 'fileutils'
require 'nakamura'
include SlingInterface
require 'nakamura/users'
include SlingUsers
require 'rubygems'
require 'docsplit'
RMAGICK_BYPASS_VERSION_TEST = true
require 'RMagick'
require "getopt/long"

Dir.chdir(File.dirname(__FILE__))
MAIN_DIR = Dir.getwd
DOCS_DIR = "#{MAIN_DIR}/docs"
PREV_DIR = "#{MAIN_DIR}/previews"
LOGS_DIR = "#{MAIN_DIR}/logs"

# Override the initialize_http_header method that sling.rb overrides
# in order to properly set the referrer.
module Net::HTTPHeader
  def initialize_http_header(initheader)
    @header = {"Referer" => [$preview_referer]}
    return unless initheader
    initheader.each do |key, value|
      warn "net/http: warning: duplicated HTTP header: #{key}" if key?(key) && $VERBOSE
      @header[key.downcase] = [value.strip]
    end
  end
  def encode_kvpair(k, vs)
    if vs.nil? or vs == '' then
      "#{urlencode(k)}="
    elsif vs.kind_of?(Array)
      # In Ruby 1.8.7, Array(string-with-newlines) will split the string
      # after each embedded newline.
      Array(vs).map {|v| "#{urlencode(k)}=#{urlencode(v.to_s)}" }
    else
      "#{urlencode(k)}=#{urlencode(vs.to_s)}"
    end
  end
end

# Re-sized an image and saves the stream of bytes of the re-sized image to a new file with a specific filename.
# Note: important to read for psd image previews: http://www.rubblewebs.co.uk/imagemagick/psd.php
def resize_and_write_file(filename, filename_output, max_width, max_height = nil)
  pic = Magick::Image.read(filename).first
  img_width, img_height = pic.columns, pic.rows
  ratio = img_width.to_f / max_width.to_f

  if max_height.nil?
    max_height = img_height / ratio
  end

  img_ratio = img_width.to_f / img_height.to_f
  img_ratio > ratio ? scale_ratio = max_width.to_f / img_width : scale_ratio = max_height.to_f / img_height
  pic.resize_to_fit!(max_width, scale_ratio * img_height)

  pic.write filename_output

  nbytes, content = File.size(filename_output), nil
  File.open(filename_output, "rb") { |f| content = f.read nbytes }
  content
end

# Images have a different procedure to process, they only need to be re-sized
# this method determines if we should process this as an image.
def process_as_image?(extension)
  ['.png', '.jpg', '.gif', '.psd', '.jpeg'].include? extension
end

# Ignore the file types in the ignore.types file
def ignore_processing?(mimetype)
  File.open("../ignore.types", "r") do |f|
    while (line = f.gets)
      line.chomp!
      # ignore any commented lines and check for the extension
      if line[0] != "#" && line.eql?(mimetype) then
        return true
      end
    end
  end
  false
end

# Determine an appropriate file extension given this file's mimetype
# if the given_extension for the file corresponds to a valid extension for
# this mimetype, return it, otherwise just grab the first extension from the
# mimetype entry in mime.types and use it for the extension to create a preview
def determine_file_extension_with_mime_type(mimetype, given_extension)
  # strip off the leading . in the given extension
  if given_extension && given_extension.match(/^\./)
    given_extension = given_extension[1..-1]
  end
  File.open("../mime.types", "r") do |f|
    while (line = f.gets)
      line.chomp!
      # ignore any commented lines and check for the mimetype in the line
      if line[0] != "#" && line.include?(mimetype) then
        if line.include? given_extension
          return ".#{given_extension}"
        else
          return ".#{line.split(' ')[1]}"
        end
      end
    end
  end
  ''
end

# Post the file to the server.
# 1 based index! (necessity for the docpreviewer 3akai-ux widget), e.g: id.pagex-large.jpg
def post_file_to_server id, content, size, page_count
  @s.execute_file_post @s.url_for("system/pool/createfile.#{id}.page#{page_count}-#{size}"), "thumbnail", "thumbnail", content, "image/jpeg"
  alt_url = @s.url_for("p/#{id}/page#{page_count}.#{size}.jpg")
  @s.execute_post alt_url, {"sakai:excludeSearch" => true}
  log "Uploaded image to curl #{alt_url}"
end

@loggers = []

def log msg, level = :info
  @loggers.each { |logger| logger.send(level, msg) }
end

def setup(server, admin_password)
  # Setup loggers.
  Dir.mkdir LOGS_DIR unless File.directory? LOGS_DIR
  @loggers << Logger.new(STDOUT)
  @loggers << Logger.new("#{LOGS_DIR}/#{Date.today}.log", 'daily')
  @loggers.each { |logger| logger.level = Logger::INFO }

  @s = Sling.new(server)
  admin = User.new("admin", admin_password)
  @s.switch_user(admin)
  @s.do_login
end

# This is the main method we call at the end of the script.
def main(term_server)
  res = @s.execute_get(@s.url_for("var/search/needsprocessing.json"))
  unless res.code == '200'
    raise "Failed to retrieve list to process [#{res.code}]"
  end

  process_results = JSON.parse(res.body)['results']
  log "processing #{process_results.size} entries"
  unless process_results.size > 0
    return
  end

  # Create some temporary directories.
  Dir.mkdir DOCS_DIR unless File.directory? DOCS_DIR
  Dir.mkdir PREV_DIR unless File.directory? PREV_DIR

  # Create a temporary file in the DOCS_DIR for all the pending files and outputs all the filenames in the terminal.
  Dir.chdir DOCS_DIR
  queued_files = process_results.collect do |result|
    FileUtils.touch result['_path']
  end

  log " "
  log "Starts a new batch of queued files: #{queued_files.join(', ')}"

  Dir['*'].each do |id|
    FileUtils.rm id
    log "processing #{id}"

    begin
      meta_file = @s.execute_get @s.url_for("p/#{id}.json")
      unless meta_file.code == '200'
        raise "Failed to process: #{id}"
      end

      meta = JSON.parse meta_file.body
      mime_type = meta['_mimeType']
      given_extension = meta["sakai:fileextension"]
      extension = determine_file_extension_with_mime_type(mime_type, given_extension)
      filename = id + extension
      log "with filename: #{filename}"

      if ignore_processing?(mime_type) || extension.eql?('')
        if extension.eql?('')
          log "ignoring processing of #{filename}, no preview can be generated for files without a known mime type"
          log "The file's original extension was #{given_extension}, and it's mime type is #{mime_type}"
        else
          log "ignoring processing of #{filename}, no preview can be generated for #{mime_type} files"
        end
      else
        # Making a local copy of the file.
        content_file = @s.execute_get @s.url_for("p/#{id}")
        unless content_file.code == '200'
          raise "Failed to process file: #{id}, status: #{content_file.code}"
        end
        File.open(filename, 'wb') { |f| f.write content_file.body }

        if process_as_image? extension
          page_count = 1
          filename_thumb = 'thumb.jpg'

          content = resize_and_write_file filename, filename_thumb, 900
          post_file_to_server id, content, :normal, page_count

          content = resize_and_write_file filename, filename_thumb, 180, 225
          post_file_to_server id, content, :small, page_count

          FileUtils.rm DOCS_DIR + "/#{filename_thumb}"
        else
          begin
            # Check if user wants autotagging
            user_id = meta["sakai:pool-content-created-for"]
            user_file = @s.execute_get @s.url_for("/system/me?uid=#{user_id}")
            unless user_file.code == '200'
              raise "Failed to get user: #{uid}"
            end
            user = JSON.parse(user_file.body)
            if user["user"]["properties"]["isAutoTagging"] != "false"
              # Get text from the document
              Docsplit.extract_text filename, :ocr => false
              text_content = IO.read(id + ".txt")
              postData = Net::HTTP.post_form(URI.parse(term_server), {'context' => text_content})
              if postData != nil
                postData = JSON.parse postData.body
              end
              tags = ""
              if postData != nil
                for i in (0..postData.length - 1)
                  tags += "- " + postData[i] + "\n"
                  postData[i] = "/tags/#{postData[i]}"
                end
              end
              # Generate tags for document
              @s.execute_post @s.url_for("p/#{id}"), {':operation' => 'tag', 'key' => postData}
              log "Generate tags for #{id}, #{postData}"
              FileUtils.rm id + ".txt"
              admin_id = "admin"
              origin_file_name = meta["sakai:pooled-content-file-name"]
              if postData != nil && postData.length > 0 && user["user"]["properties"]["sendTagMsg"] && user["user"]["properties"]["sendTagMsg"] != "false"
                msg_body = "We have automatically added the following tags for #{origin_file_name}:\n\n #{tags}\n\nThese tags were created to aid in the discoverability of your content.\n\nRegards, \nThe Sakai Team"
                @s.execute_post(@s.url_for("~#{admin_id}/message.create.html"), {
                  "sakai:type" => "internal",
                  "sakai:sendstate" => "pending",
                  "sakai:messagebox" => "outbox",
                  "sakai:to" => "internal:#{user_id}",
                  "sakai:from" => "#{admin_id}",
                  "sakai:subject" => "We've added some tags to #{origin_file_name}",
                  "sakai:body" => msg_body,
                  "_charset_" => "utf-8",
                  "sakai:category" => "message"
                })
                log "sending message from #{admin_id} user to #{user_id}"
              end
            end
          rescue Exception => msg
            log "failed to generate document tags: #{msg}", :warn
          end

          # Generating image previews of the document.
          Docsplit.extract_images filename, :size => '1000x', :format => :jpg

          # Skip documents with a page count of 0, just to be sure.
          next if Dir[id + '_*'].size == 0

          Dir.mkdir PREV_DIR + "/#{id}" unless File.directory? PREV_DIR + "/#{id}"

          # Moving these previews to another directory: "PREVS_DIR/filename/index.jpg".
          Dir[id + '_*'].each_with_index do |preview, index|
            FileUtils.mv "#{id}_#{index + 1}.jpg", "#{PREV_DIR}/#{id}/#{index}.jpg"
          end

          Dir.chdir PREV_DIR + "/#{id}"
          page_count = Dir["*"].size

          # Upload each preview and create+upload a thumbnail.
          for index in (0..page_count - 1)
            filename_p = "#{index}.jpg"
            # Upload the generated preview of this page.
            nbytes, content = File.size(filename_p), nil
            File.open(filename_p, "rb") { |f| content = f.read nbytes }
            post_file_to_server id, content, :large, index + 1

            # Generate 2 thumbnails and upload them to the server.
            filename_thumb = File.basename(filename_p, '.*') + '.normal.jpg'
            content = resize_and_write_file filename_p, filename_thumb, 700
            post_file_to_server id, content, :normal, index + 1

            filename_thumb = File.basename(filename_p, '.*') + '.small.jpg'
            content = resize_and_write_file filename_p, filename_thumb, 180, 225
            post_file_to_server id, content, :small, index + 1
          end

          FileUtils.remove_dir PREV_DIR + "/#{id}"
        end

        # Pass on the page_count
        @s.execute_post @s.url_for("p/#{id}"), {"sakai:pagecount" => page_count, "sakai:hasPreview" => "true"}

        # Change to the documents directory otherwise we won't find the next file.
        Dir.chdir DOCS_DIR
      end
    rescue Exception => msg
      # Output a timestamp + the error message whenever an exception is raised
      # and flag this file as failed for processing.
      log "error generating preview/thumbnail (ID: #{id}): #{msg.inspect}\n#{msg.backtrace.join("\n")}", :warn
      @s.execute_post @s.url_for("p/#{id}"), {"sakai:processing_failed" => "true"}
    ensure
      # No matter what we flag the file as processed and delete the temp copied file.
      @s.execute_post @s.url_for("p/#{id}"), {"sakai:needsprocessing" => "false"}
      FileUtils.rm_f DOCS_DIR + "/#{filename}"
    end
  end

  FileUtils.remove_dir PREV_DIR
  FileUtils.remove_dir DOCS_DIR
end

def usage
  puts "usage: #{$0} [-h|--help] [-s|--server] <server> [-p|--password] <adminpassword> [-t|--term] <term-extraction address> [-i|--interval] [interval]"
  puts "example: #{$0} http://localhost:8080/ admin http://localhost:8085/ 20"
end

## Parse command line opts and call main ##
opt = Getopt::Long.getopts(
  ["--help", "-h", Getopt::BOOLEAN],
  ["--server", "-s", Getopt::REQUIRED],
  ["--password", "-p", Getopt::REQUIRED],
  ["--term", "-t", Getopt::REQUIRED],
  ["--interval", "-i", Getopt::REQUIRED],
  ["--count", "-n", Getopt::REQUIRED]
)

if opt['help'] || not(opt['server'] && opt['password'] && opt['term'])
  usage()
else
  setup(opt['server'], opt['password'])
  $preview_referer = opt['server']
  interval = opt['interval'] || 15
  interval = Integer(interval)
  count = opt['count'] || 0
  count = Integer(count)
  begin
    main(opt['term'])
    if opt['count']
      if count > 1
        count -= 1
      else
        break
      end
    end
  end while sleep(interval)
end
