package dev.cardadvisor.api;

import dev.cardadvisor.domain.FixedCostCalculator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @Transactional
public class FixedCostService {
 private final JdbcTemplate db;private final FixedCostCalculator calculator=new FixedCostCalculator();
 private static final Set<String> CATEGORIES=Set.of("SHOPPING","FOOD","TRANSPORT","HOUSING","HEALTH","LEISURE","OTHER");
 public FixedCostService(JdbcTemplate db){this.db=db;}
 private Map<String,Object> one(String sql,Object...args){var rows=db.queryForList(sql,args);if(rows.isEmpty())throw new ApiError(404,"RESOURCE_NOT_FOUND");return rows.get(0);}
 private void lock(UUID user){one("SELECT revision FROM app_users WHERE id=? FOR UPDATE",user);}
 private void changed(UUID user,UUID id){db.update("UPDATE fixed_cost_plans SET version=version+1 WHERE user_id=? AND id=?",user,id);db.update("UPDATE app_users SET revision=revision+1 WHERE id=?",user);}
 private LocalDate month(String value){try{if(value==null||!value.matches("[0-9]{4}-[0-9]{2}"))throw new IllegalArgumentException();var m=YearMonth.parse(value);if(m.getYear()<1)throw new IllegalArgumentException();return m.atDay(1);}catch(Exception e){throw new ApiError(422,"INVALID_MONTH");}}
 public record PlanInput(UUID id,String name,Long amountWon,String category,Integer dayOfMonth,String effectiveMonth,Boolean active,Long version){}
 public record LinkInput(UUID transactionId,Boolean linked,Long version){}
 public record LinkedPayment(String transactionId,long netWon,boolean included,String category){}
 public record Occurrence(String id,long version,String name,long amountWon,String category,int dayOfMonth,String effectiveMonth,boolean active,String dueDate,long linkedNetWon,long includedNetWon,long remainingWon,List<LinkedPayment> payments){}
 public record MonthView(String month,String mode,Long manualRemainingWon,long scheduledRemainingWon,Long appliedRemainingWon,List<Occurrence> items){}
 private Map<String,Object> rule(UUID user,UUID id,LocalDate m){return one("SELECT * FROM fixed_cost_rules WHERE user_id=? AND plan_id=? AND effective_month<=? ORDER BY effective_month DESC LIMIT 1",user,id,m);}
 public List<Occurrence> occurrences(UUID user,String selectedMonth){
  LocalDate m=month(selectedMonth);var result=new ArrayList<Occurrence>();
  var rows=db.queryForList("SELECT p.id,p.version,r.* FROM fixed_cost_plans p JOIN LATERAL (SELECT * FROM fixed_cost_rules WHERE user_id=p.user_id AND plan_id=p.id AND effective_month<=? ORDER BY effective_month DESC LIMIT 1) r ON true WHERE p.user_id=? ORDER BY r.name,p.id",m,user);
  for(var r:rows){UUID id=(UUID)r.get("id");
   var links=db.queryForList("SELECT e.id,e.amount_won,e.included,e.category,COALESCE((SELECT sum(c.amount_won) FROM cancellations c WHERE c.user_id=e.user_id AND c.transaction_id=e.id),0) AS refunded FROM fixed_cost_links l JOIN ledger_entries e ON e.user_id=l.user_id AND e.id=l.transaction_id WHERE l.user_id=? AND l.plan_id=? AND l.month=? ORDER BY e.purchased_at,e.id",user,id,m);
   var payments=links.stream().map(l->new FixedCostCalculator.Payment(((Number)l.get("amount_won")).longValue(),((Number)l.get("refunded")).longValue(),(Boolean)l.get("included"))).toList();
   long expected=((Number)r.get("amount_won")).longValue();boolean active=(Boolean)r.get("active");var balance=calculator.calculate(expected,active,payments);
   var detail=links.stream().map(l->new LinkedPayment(l.get("id").toString(),((Number)l.get("amount_won")).longValue()-((Number)l.get("refunded")).longValue(),(Boolean)l.get("included"),(String)l.get("category"))).toList();
   int day=((Number)r.get("day_of_month")).intValue();
   result.add(new Occurrence(id.toString(),((Number)r.get("version")).longValue(),(String)r.get("name"),expected,(String)r.get("category"),day,r.get("effective_month").toString().substring(0,7),active,calculator.dueDate(YearMonth.from(m),day).toString(),balance.linkedNetWon(),balance.includedNetWon(),balance.remainingWon(),detail));
  }return result;
 }
 public long remaining(UUID user,String m){return occurrences(user,m).stream().mapToLong(Occurrence::remainingWon).reduce(0,Math::addExact);}
 public MonthView view(UUID user,String m){lock(user);var items=occurrences(user,m);long scheduled=items.stream().mapToLong(Occurrence::remainingWon).reduce(0,Math::addExact);
  var budget=db.queryForList("SELECT COALESCE(payload->>'fixedCostMode','MANUAL') AS mode,(payload->>'fixedRemainingWon')::bigint AS manual FROM budgets WHERE user_id=? AND month=?",user,month(m));
  if(budget.isEmpty())return new MonthView(m,"UNSET",null,scheduled,null,items);
  String mode=(String)budget.get(0).get("mode");long manual=((Number)budget.get(0).get("manual")).longValue();return new MonthView(m,mode,manual,scheduled,"SCHEDULED".equals(mode)?scheduled:manual,items);
 }
 public MonthView save(UUID user,PlanInput in){
  lock(user);LocalDate effective=month(in.effectiveMonth());
  if(in.id()==null||in.version()==null||in.version()<0||in.name()==null||in.name().isBlank()||in.name().length()>80||in.amountWon()==null||in.amountWon()<1||in.amountWon()>100_000_000||in.dayOfMonth()==null||in.dayOfMonth()<1||in.dayOfMonth()>31||in.category()==null||!CATEGORIES.contains(in.category())||in.active()==null)throw new ApiError(422,"INVALID_FIXED_COST");
  var plans=db.queryForList("SELECT version FROM fixed_cost_plans WHERE user_id=? AND id=?",user,in.id());
  if(plans.isEmpty()){
   if(in.version()!=0)throw new ApiError(404,"RESOURCE_NOT_FOUND");
   if(db.queryForObject("SELECT count(*) FROM fixed_cost_plans WHERE id=?",Integer.class,in.id())>0)throw new ApiError(404,"RESOURCE_NOT_FOUND");
   db.update("INSERT INTO fixed_cost_plans(id,user_id,version) VALUES (?,?,1)",in.id(),user);
  }else{
   if(in.version()!=((Number)plans.get(0).get("version")).longValue())throw new ApiError(409,"VERSION_CONFLICT");
   db.update("UPDATE fixed_cost_plans SET version=version+1 WHERE user_id=? AND id=?",user,in.id());
  }
  db.update("INSERT INTO fixed_cost_rules(user_id,plan_id,effective_month,name,amount_won,category,day_of_month,active) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(user_id,plan_id,effective_month) DO UPDATE SET name=EXCLUDED.name,amount_won=EXCLUDED.amount_won,category=EXCLUDED.category,day_of_month=EXCLUDED.day_of_month,active=EXCLUDED.active",user,in.id(),effective,in.name().strip(),in.amountWon(),in.category(),in.dayOfMonth(),in.active());
  db.update("UPDATE app_users SET revision=revision+1 WHERE id=?",user);return view(user,in.effectiveMonth());
 }
 public MonthView link(UUID user,UUID id,String m,LinkInput in){
  lock(user);LocalDate selected=month(m);var plan=one("SELECT version FROM fixed_cost_plans WHERE user_id=? AND id=?",user,id);
  if(in.version()==null||in.version()<1||in.transactionId()==null||in.linked()==null)throw new ApiError(422,"INVALID_FIXED_LINK");
  if(in.version()!=((Number)plan.get("version")).longValue())throw new ApiError(409,"VERSION_CONFLICT");
  var rule=rule(user,id,selected);var entry=one("SELECT purchased_at FROM ledger_entries WHERE user_id=? AND id=?",user,in.transactionId());
  if(!entry.get("purchased_at").toString().substring(0,7).equals(m))throw new ApiError(422,"FIXED_LINK_MONTH_MISMATCH");
  var old=db.queryForList("SELECT plan_id,month FROM fixed_cost_links WHERE user_id=? AND transaction_id=?",user,in.transactionId());
  if(!old.isEmpty()&&(!old.get(0).get("plan_id").equals(id)||!old.get(0).get("month").toString().equals(selected.toString())))throw new ApiError(409,"TRANSACTION_ALREADY_LINKED");
  if(in.linked()&&old.isEmpty()){
   if(!Boolean.TRUE.equals(rule.get("active")))throw new ApiError(422,"FIXED_COST_INACTIVE");
   db.update("INSERT INTO fixed_cost_links(user_id,plan_id,month,transaction_id) VALUES (?,?,?,?)",user,id,selected,in.transactionId());changed(user,id);
  }else if(!in.linked()&&!old.isEmpty()){
   db.update("DELETE FROM fixed_cost_links WHERE user_id=? AND plan_id=? AND month=? AND transaction_id=?",user,id,selected,in.transactionId());changed(user,id);
  }
  return view(user,m);
 }
}
