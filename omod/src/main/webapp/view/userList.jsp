<%@ include file="/WEB-INF/view/module/personalhr/template/include.jsp" %>
<<personalhr:require privilege="PHR Administrator" otherwise="/phr/login.htm" />

<openmrs:require privilege="PHR All Patients Access" otherwise="/phr/login.htm" redirect="/phr/user.list" />
<spring:message var="pageTitle" code="User.manage.titlebar" scope="page"/>

<%@ include file="/WEB-INF/view/module/personalhr/template/header.jsp" %>

<h2><spring:message code="User.manage.title"/></h2>

<a href="user.form"><spring:message code="User.add"/></a>

<br/><br/>

<b class="boxHeader"><spring:message code="User.list.title"/></b>

<openmrs:htmlInclude file="/dwr/util.js" />
<openmrs:htmlInclude file="/scripts/dojo/dojo.js" />
<openmrs:htmlInclude file="/dwr/interface/DWRUserService.js" />

<script type="text/javascript">
	dojo.require("dojo.widget.openmrs.UserSearch");
	
	var searchWidget;
	
	var getSystemId = function(u) {
		if (typeof u == 'string') return u;
		s = " &nbsp; " + u.systemId;;	
		if (u.retired)
			s = "<span class='retired'>" + s + "</span>";
		return s;
	}
	
	var getUsername = function(u) {
		if (typeof u == 'string' || u.username == null) return '';
		return " &nbsp; " + u.username;
	}
	
	var getRoles = function(u) {
		if (typeof u == 'string') return '';	
		return " &nbsp; " + u.roles;
	}
	
	dojo.addOnLoad( function() {
		
		searchWidget = dojo.widget.manager.getWidgetById("uSearch");			
		
		dojo.event.topic.subscribe("uSearch/select", 
			function(msg) {
				document.location = "user.form?userId=" + msg.objs[0].userId;
			}
		);
		
		searchWidget.allowAutoJump = function() {
			return this.text && this.text.length > 1;
		}
		
		searchWidget.allowAutoJump = function() {
			return this.text && this.text.length > 1;
		}
		
		searchWidget.showAll();
		searchWidget.inputNode.focus();
		searchWidget.inputNode.select();
		
	});
	
</script>

<div class="box">
	<div dojoType="UserSearch" widgetId="uSearch" searchLabel='<spring:message code="User.find"/>' showIncludeRetired="true" showRoles="true"></div>
</div>

<br/><br/>

<%@ include file="/WEB-INF/view/module/personalhr/template/footer.jsp" %> 