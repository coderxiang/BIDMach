package BIDMach

import BIDMat.{Mat,BMat,CMat,DMat,FMat,IMat,HMat,GMat,GIMat,GSMat,SMat,SDMat}
import BIDMat.MatFunctions._
import BIDMat.SciFunctions._


class LDAModel(override val opts:LDAModel.Options = new LDAModel.Options) extends FactorModel(opts) { 
  var mm:Mat = null
  var alpha:Mat = null
  
  var traceMem = false
  
  override def init(datasource:DataSource) = {
    super.init(datasource)
    mm = modelmats(0)
    modelmats = new Array[Mat](2)
    modelmats(0) = mm
    modelmats(1) = mm.ones(mm.nrows, 1)
    updatemats = new Array[Mat](2)
    updatemats(0) = mm.zeros(mm.nrows, mm.ncols)
    updatemats(1) = mm.zeros(mm.nrows, 1)
  }
  
  def uupdate(sdata:Mat, user:Mat):Unit = {
    if (opts.putBack < 0) user.set(1f)
	  for (i <- 0 until opts.uiter) {
	  	val preds = DDS(mm, user, sdata)	
	  	if (traceMem) println("uupdate %d %d %d, %d %f %d" format (mm.GUID, user.GUID, sdata.GUID, preds.GUID, GPUmem._1, getGPU))
	  	val dc = sdata.contents
	  	val pc = preds.contents
	  	max(opts.weps, pc, pc)
	  	pc ~ dc / pc
	  	val unew = user ∘ (mm * preds) + opts.alpha
	  	if (traceMem) println("uupdate %d %d %d, %d %d %d %d %f %d" format (mm.GUID, user.GUID, sdata.GUID, preds.GUID, dc.GUID, pc.GUID, unew.GUID, GPUmem._1, getGPU))
	  	if (opts.exppsi) exppsi(unew, unew)
	  	user <-- unew   
	  }	
//    println("user %g %g" format (mini(mini(user,1),2).dv, maxi(maxi(user,1),2).dv))
  }
  
  def mupdate(sdata:Mat, user:Mat):Unit = {
    val preds = DDS(mm, user, sdata)
    val dc = sdata.contents
    val pc = preds.contents
    max(opts.weps, pc, pc)
    pc ~ dc / pc
    val ud = user *^ preds
    ud ~ ud ∘ mm
    ud ~ ud + opts.beta
  	updatemats(0) <-- ud  
  	sum(ud, 2, updatemats(1))
  	if (traceMem) println("mupdate %d %d %d %d" format (sdata.GUID, user.GUID, ud.GUID, updatemats(0).GUID))
  }
  
  def evalfun(sdata:Mat, user:Mat):FMat = {  
  	val preds = DDS(mm, user, sdata)
  	val dc = sdata.contents
  	val pc = preds.contents
  	max(opts.weps, pc, pc)
  	ln(pc, pc)
  	val sdat = sum(sdata,1)
  	val mms = sum(mm,2)
  	val suu = ln(mms ^* user)
  	if (traceMem) println("evalfun %d %d %d, %d %d %d, %d %f" format (sdata.GUID, user.GUID, preds.GUID, pc.GUID, sdat.GUID, mms.GUID, suu.GUID, GPUmem._1))
  	val vv = ((pc ddot dc) - (sdat ddot suu))/sum(sdat,2).dv
  	row(vv, math.exp(-vv))
  }
}

object LDAModel  {
  class Options extends FactorModel.Options {
    var LDAeps = 1e-9
    var exppsi = true
    var alpha = 0.001f
    var beta = 0.0001f
  }
  
  def mkLDAmodel(fopts:Model.Options) = {
  	new LDAModel(fopts.asInstanceOf[LDAModel.Options])
  }
  
  def mkUpdater(nopts:Updater.Options) = {
  	new IncNormUpdater(nopts.asInstanceOf[IncNormUpdater.Options])
  } 
  
  def learn(mat0:Mat) = {
  	new Learner(new MatDataSource(Array(mat0:Mat)), new LDAModel(), null, new IncNormUpdater(), new Learner.Options)
  }
  
  def learnBatch(mat0:Mat) = {	
  	new Learner(new MatDataSource(Array(mat0:Mat)), new LDAModel(), null, new BatchNormUpdater(), new Learner.Options)
  }
  
  def learnFPar(
    nstart:Int=FilesDataSource.encodeDate(2012,3,1,0),
		nend:Int=FilesDataSource.encodeDate(2012,12,1,0)
		) = { 	
  	new LearnFParModel(
  			new LDAModel.Options, mkLDAmodel _, 
  	    new IncNormUpdater.Options, mkUpdater _, 
  	    (n:Int, i:Int) => SFilesDataSource.twitterWords(nstart, nend, n, i)
  	    )
  }
  
  def learnFParx(
    nstart:Int=FilesDataSource.encodeDate(2012,3,1,0),
		nend:Int=FilesDataSource.encodeDate(2012,12,1,0)
		) = {	
  	new LearnFParModelx(
  	    SFilesDataSource.twitterWords(nstart, nend),
  	    new LDAModel.Options, mkLDAmodel _, 
  	    new IncNormUpdater.Options, mkUpdater _ 
  	    )
  }
}


